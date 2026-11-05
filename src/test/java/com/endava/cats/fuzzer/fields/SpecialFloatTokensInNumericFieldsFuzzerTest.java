package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class SpecialFloatTokensInNumericFieldsFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private ProcessingArguments processingArguments;
    private SpecialFloatTokensInNumericFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        fuzzer = new SpecialFloatTokensInNumericFieldsFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldFuzzNumberFieldsWithSpecialFloatTokens() {
        FuzzingData data = FuzzingData.builder()
                .payload("{\"amount\":19.99,\"count\":2,\"name\":\"item\"}")
                .allFieldsByHttpMethod(Set.of("amount", "count", "name"))
                .requestPropertyTypes(Map.of(
                        "amount", new NumberSchema(),
                        "count", new IntegerSchema(),
                        "name", new StringSchema()))
                .build();

        fuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(3)).execute(Mockito.any());
    }

    @Test
    void shouldSkipWhenNoNumberFieldsExist() {
        FuzzingData data = FuzzingData.builder()
                .payload("{\"count\":2,\"name\":\"item\"}")
                .allFieldsByHttpMethod(Set.of("count", "name"))
                .requestPropertyTypes(Map.of(
                        "count", new IntegerSchema(),
                        "name", new StringSchema()))
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldReturnExpectedMetadata() {
        Assertions.assertThat(fuzzer.getFuzzedValues(1.0)).containsExactly("NaN", "Infinity", "-Infinity");
        Assertions.assertThat(fuzzer.description()).contains("number").contains("IEEE-754 special float token");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).containsExactly(HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("SpecialFloatTokensInNumericFieldsFuzzer");
    }
}
