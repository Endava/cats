package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
class ConfirmationFieldsMismatchFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private ConfirmationFieldsMismatchFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        fuzzer = new ConfirmationFieldsMismatchFuzzer(simpleExecutor);
    }

    @ParameterizedTest
    @CsvSource({
            "confirmPassword,password",
            "passwordConfirmation,password",
            "repeatEmail,email",
            "verifyAccountNumber,accountnumber",
            "retypePin,pin"
    })
    void shouldExtractBaseFieldName(String confirmLeaf, String expectedBase) {
        String normalized = com.endava.cats.util.WordUtils.normalizeLeaf(confirmLeaf);
        Assertions.assertThat(ConfirmationFieldsMismatchFuzzer.extractBaseFieldName(normalized))
                .contains(expectedBase);
    }

    @Test
    void shouldFuzzMatchingConfirmationPairs() {
        FuzzingData data = FuzzingData.builder()
                .path("/register")
                .method(HttpMethod.POST)
                .payload("{\"password\":\"Secret123!\",\"confirmPassword\":\"Secret123!\",\"username\":\"alice\"}")
                .allFieldsByHttpMethod(Set.of("password", "confirmPassword", "username"))
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        SimpleExecutorContext context = captor.getValue();
        Assertions.assertThat(context.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(context.getPayload())
                .contains("\"password\":\"Secret123!\"")
                .contains("\"confirmPassword\":\"Xecret123!\"");
    }

    @Test
    void shouldSkipWhenNoConfirmationPairExists() {
        FuzzingData data = FuzzingData.builder()
                .path("/register")
                .method(HttpMethod.POST)
                .payload("{\"password\":\"Secret123!\",\"username\":\"alice\"}")
                .allFieldsByHttpMethod(Set.of("password", "username"))
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldSkipEmptyPayload() {
        FuzzingData data = FuzzingData.builder()
                .path("/register")
                .method(HttpMethod.POST)
                .payload("")
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldBuildMismatchedValuesPreservingLengthAndType() {
        Assertions.assertThat(ConfirmationFieldsMismatchFuzzer.buildMismatchedValue(1234)).isEqualTo(1235L);
        Assertions.assertThat(ConfirmationFieldsMismatchFuzzer.buildMismatchedValue("123456")).isEqualTo("923456");
        Assertions.assertThat(ConfirmationFieldsMismatchFuzzer.buildMismatchedValue("Xbc")).isEqualTo("Ybc");
        Assertions.assertThat(ConfirmationFieldsMismatchFuzzer.buildMismatchedValue("")).isEqualTo("catsMismatch1");
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("ConfirmationFieldsMismatchFuzzer");
    }
}
