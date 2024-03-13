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
import org.mockito.Mock;
import org.mockito.Mockito;

@QuarkusTest
class VeryLargeUnicodeStringsInFieldsFuzzerTest {
    @Mock
    private ProcessingArguments processingArguments;

    private VeryLargeUnicodeStringsInFieldsFuzzer veryLargeUnicodeStringsInFieldsFuzzer;

    @BeforeEach
    void setup() {
        processingArguments = Mockito.mock(ProcessingArguments.class);
        veryLargeUnicodeStringsInFieldsFuzzer = new VeryLargeUnicodeStringsInFieldsFuzzer(null, null, null, processingArguments);
    }

    @Test
    void shouldOverrideDefaultMethods() {
        Assertions.assertThat(veryLargeUnicodeStringsInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeUnicodeStringsInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldGetPayloadSize() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20000);

        Assertions.assertThat(veryLargeUnicodeStringsInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0).getData().toString()).hasSizeGreaterThan(20000);
    }

    @Test
    void shouldGenerateLessThan500() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20);
        Assertions.assertThat(veryLargeUnicodeStringsInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0).getData().toString()).hasSize(20 + "cats".length());
    }

    @Test
    void shouldOverrideToNotMatchPatterns() {
        Assertions.assertThat(veryLargeUnicodeStringsInFieldsFuzzer.shouldCheckForFuzzedValueMatchingPattern()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"POST,true", "GET,false"})
    void shouldNotMatchResponseContentType(HttpMethod method, boolean expected) {
        FuzzingData data = FuzzingData.builder().method(method).build();
        Assertions.assertThat(veryLargeUnicodeStringsInFieldsFuzzer.shouldMatchContentType(data)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"POST,true", "GET,false"})
    void shouldMatchResponseSchema(HttpMethod method, boolean expected) {
        FuzzingData data = FuzzingData.builder().method(method).build();
        Assertions.assertThat(veryLargeUnicodeStringsInFieldsFuzzer.shouldMatchResponseSchema(data)).isEqualTo(expected);
    }
}