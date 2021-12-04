package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class VeryLargeValuesInFieldsFuzzerTest {
    private VeryLargeValuesInFieldsFuzzer veryLargeValuesInFieldsFuzzer;
    private ProcessingArguments processingArguments;

    @BeforeEach
    void setup() {
        processingArguments = Mockito.mock(ProcessingArguments.class);
        veryLargeValuesInFieldsFuzzer = new VeryLargeValuesInFieldsFuzzer(null, null, null, null, processingArguments);
    }

    @Test
    void givenANewVeryLargeStringsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheVeryLargeStringsFuzzer() {
        Assertions.assertThat(veryLargeValuesInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeValuesInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();

    }

    @Test
    void shouldGetPayloadSize() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20000);

        Assertions.assertThat(veryLargeValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0).getData()).hasSize(20000);
    }
}
