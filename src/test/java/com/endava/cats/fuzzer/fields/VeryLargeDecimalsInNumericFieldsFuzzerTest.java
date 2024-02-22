package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
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

@QuarkusTest
class VeryLargeDecimalsInNumericFieldsFuzzerTest {
    private VeryLargeDecimalsInNumericFieldsFuzzer veryLargeDecimalsInNumericFieldsFuzzer;
    private ProcessingArguments processingArguments;

    @BeforeEach
    void setup() {
        processingArguments = Mockito.mock(ProcessingArguments.class);
        veryLargeDecimalsInNumericFieldsFuzzer = new VeryLargeDecimalsInNumericFieldsFuzzer(null, null, null, processingArguments);
    }

    @Test
    void shouldReturnDescriptionAndTypeOfData() {
        Assertions.assertThat(veryLargeDecimalsInNumericFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeDecimalsInNumericFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();

    }

    @Test
    void shouldGetPayloadSizeForNumberSchema() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20000);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("myField", new NumberSchema()));

        Assertions.assertThat(veryLargeDecimalsInNumericFieldsFuzzer.getFieldFuzzingStrategy(data, "myField").get(0).getData().toString()).hasSize(20041);
    }

    @Test
    void shouldGetPayloadSizeForIntegerSchema() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(30000);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("myField", new IntegerSchema()));

        Assertions.assertThat(veryLargeDecimalsInNumericFieldsFuzzer.getFieldFuzzingStrategy(data, "myField").get(0).getData().toString()).hasSize(30041);
    }

    @Test
    void shouldSkipWhenNotNumericFields() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(30000);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("myField", new StringSchema()));

        Assertions.assertThat(veryLargeDecimalsInNumericFieldsFuzzer.getFieldFuzzingStrategy(data, "myField").get(0).isSkip()).isTrue();
    }
}
