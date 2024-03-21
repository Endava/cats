package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

@QuarkusTest
class VeryLargeStringsInFieldsFuzzerTest {
    private VeryLargeStringsInFieldsFuzzer veryLargeStringsInFieldsFuzzer;
    private ProcessingArguments processingArguments;

    @BeforeEach
    void setup() {
        processingArguments = Mockito.mock(ProcessingArguments.class);
        veryLargeStringsInFieldsFuzzer = new VeryLargeStringsInFieldsFuzzer(null, null, null, processingArguments);
    }

    @Test
    void givenANewVeryLargeStringsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheVeryLargeStringsFuzzer() {
        Assertions.assertThat(veryLargeStringsInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeStringsInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();

    }

    @Test
    void shouldGetPayloadSize() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20000);

        Assertions.assertThat(veryLargeStringsInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0).getData().toString()).hasSize(20000);
    }

    @Test
    void shouldOverrideToNotMatchPatterns() {
        Assertions.assertThat(veryLargeStringsInFieldsFuzzer.shouldCheckForFuzzedValueMatchingPattern()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"POST,true", "GET,false"})
    void shouldNotMatchResponseContentType(HttpMethod method, boolean expected) {
        FuzzingData data = FuzzingData.builder().method(method).build();
        Assertions.assertThat(veryLargeStringsInFieldsFuzzer.shouldMatchContentType(data)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"POST,true", "GET,false"})
    void shouldMatchResponseSchema(HttpMethod method, boolean expected) {
        FuzzingData data = FuzzingData.builder().method(method).build();
        Assertions.assertThat(veryLargeStringsInFieldsFuzzer.shouldMatchResponseSchema(data)).isEqualTo(expected);
    }
}
