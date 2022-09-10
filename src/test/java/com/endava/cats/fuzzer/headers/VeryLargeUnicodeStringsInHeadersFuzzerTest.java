package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class VeryLargeUnicodeStringsInHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private ProcessingArguments processingArguments;

    private VeryLargeUnicodeStringsInHeadersFuzzer veryLargeUnicodeStringsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        veryLargeUnicodeStringsInHeadersFuzzer = new VeryLargeUnicodeStringsInHeadersFuzzer(serviceCaller, testCaseListener, processingArguments);
    }

    @Test
    void givenANewLargeValuesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLargeValuesInHeadersFuzzer() {
        Assertions.assertThat(veryLargeUnicodeStringsInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeUnicodeStringsInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(veryLargeUnicodeStringsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(veryLargeUnicodeStringsInHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldGetPayloadSize() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20);
        Assertions.assertThat(veryLargeUnicodeStringsInHeadersFuzzer.fuzzStrategy().get(0).getData().toString()).hasSize(20 + "cats".length());
    }
}