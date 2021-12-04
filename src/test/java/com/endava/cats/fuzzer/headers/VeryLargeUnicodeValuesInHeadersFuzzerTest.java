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
class VeryLargeUnicodeValuesInHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private ProcessingArguments processingArguments;

    private VeryLargeUnicodeValuesInHeadersFuzzer veryLargeUnicodeValuesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        veryLargeUnicodeValuesInHeadersFuzzer = new VeryLargeUnicodeValuesInHeadersFuzzer(serviceCaller, testCaseListener, processingArguments);
    }

    @Test
    void givenANewLargeValuesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLargeValuesInHeadersFuzzer() {
        Assertions.assertThat(veryLargeUnicodeValuesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeUnicodeValuesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(veryLargeUnicodeValuesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
    }

    @Test
    void shouldGetPayloadSize() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20);
        Assertions.assertThat(veryLargeUnicodeValuesInHeadersFuzzer.fuzzStrategy().get(0).getData()).hasSize(20 + "cats".length());
    }
}