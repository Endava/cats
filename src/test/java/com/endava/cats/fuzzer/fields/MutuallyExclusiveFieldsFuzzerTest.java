package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class MutuallyExclusiveFieldsFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private MutuallyExclusiveFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        fuzzer = new MutuallyExclusiveFieldsFuzzer(simpleExecutor);
    }

    @ParameterizedTest
    @CsvSource({
            "discountAmount,discountPercentage",
            "creditAmount,debitAmount",
            "includeIds,excludeIds",
            "expiresAt,ttlSeconds"
    })
    void shouldFindMutuallyExclusiveSiblingPairs(String first, String second) {
        FuzzingData data = FuzzingData.builder()
                .allFieldsByHttpMethod(Set.of(first, second, "unrelated"))
                .build();

        Assertions.assertThat(MutuallyExclusiveFieldsFuzzer.findExclusivePairs(data))
                .containsExactly(new MutuallyExclusiveFieldsFuzzer.ExclusivePair(first, second));
    }

    @Test
    void shouldFuzzMutuallyExclusiveDiscountFields() {
        FuzzingData data = FuzzingData.builder()
                .path("/coupons")
                .method(HttpMethod.POST)
                .payload("{\"discountAmount\":0,\"discountPercentage\":15,\"code\":\"SAVE\"}")
                .allFieldsByHttpMethod(Set.of("discountAmount", "discountPercentage", "code"))
                .requestPropertyTypes(Map.of(
                        "discountAmount", new NumberSchema(),
                        "discountPercentage", new IntegerSchema(),
                        "code", new StringSchema()))
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        SimpleExecutorContext context = captor.getValue();
        Assertions.assertThat(context.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(context.getPayload())
                .contains("\"discountAmount\":10")
                .contains("\"discountPercentage\":15");
    }

    @Test
    void shouldSkipWhenNoMutuallyExclusivePairExists() {
        FuzzingData data = FuzzingData.builder()
                .path("/coupons")
                .method(HttpMethod.POST)
                .payload("{\"discountAmount\":10,\"code\":\"SAVE\"}")
                .allFieldsByHttpMethod(Set.of("discountAmount", "code"))
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldSkipEmptyPayload() {
        FuzzingData data = FuzzingData.builder()
                .path("/coupons")
                .method(HttpMethod.POST)
                .payload("")
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("MutuallyExclusiveFieldsFuzzer");
    }

    @ParameterizedTest
    @CsvSource(value = {"\"\"|string|catsActiveValue", "\"x\"|string|x", "\"0\"|integer|10", "\"0\"|number|10"}, delimiter = '|')
    void shouldActivateStringAndTypedNumericValues(String value, String type, String expected) {
        Schema<?> schema = switch (type) {
            case "integer" -> new IntegerSchema();
            case "number" -> new NumberSchema();
            default -> new StringSchema();
        };
        FuzzingData data = FuzzingData.builder().payload("{\"value\":" + value + "}")
                .requestPropertyTypes(Map.of("value", schema)).build();
        Assertions.assertThat(String.valueOf(MutuallyExclusiveFieldsFuzzer.activeValue(data, "value"))).isEqualTo(expected);
    }

    @Test
    void shouldUseFallbackForAbsentValuesWithoutSchema() {
        Assertions.assertThat(MutuallyExclusiveFieldsFuzzer.activeValue(FuzzingData.builder().payload("{}").build(), "missing"))
                .isEqualTo("catsActiveValue");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"credit\":1}", "{\"debit\":1}"})
    void shouldSkipPairsMissingFromPayload(String payload) {
        fuzzer.fuzz(FuzzingData.builder().payload(payload).allFieldsByHttpMethod(Set.of("credit", "debit")).build());
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldMatchOnlyFieldsUnderSameParent() {
        Assertions.assertThat(MutuallyExclusiveFieldsFuzzer.findExclusivePairs(FuzzingData.builder()
                .allFieldsByHttpMethod(Set.of("a#credit", "a#debit", "b#debit", "debit")).build()))
                .containsExactly(new MutuallyExclusiveFieldsFuzzer.ExclusivePair("a#credit", "a#debit"));
    }

}
