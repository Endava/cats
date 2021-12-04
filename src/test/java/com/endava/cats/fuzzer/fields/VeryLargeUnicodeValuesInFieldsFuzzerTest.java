package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

@QuarkusTest
class VeryLargeUnicodeValuesInFieldsFuzzerTest {
    @Mock
    private ProcessingArguments processingArguments;

    private VeryLargeUnicodeValuesInFieldsFuzzer veryLargeUnicodeValuesInFieldsFuzzer;

    @BeforeEach
    void setup() {
        processingArguments = Mockito.mock(ProcessingArguments.class);
        veryLargeUnicodeValuesInFieldsFuzzer = new VeryLargeUnicodeValuesInFieldsFuzzer(null, null, null, null, processingArguments);
    }

    @Test
    void shouldOverrideDefaultMethods() {
        Assertions.assertThat(veryLargeUnicodeValuesInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeUnicodeValuesInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldGetPayloadSize() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20000);

        Assertions.assertThat(veryLargeUnicodeValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0).getData()).hasSizeGreaterThan(20000);
    }

    @Test
    void shouldGenerateLessThan500() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20);
        Assertions.assertThat(veryLargeUnicodeValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0).getData()).hasSize(20 + "cats".length());
    }
}