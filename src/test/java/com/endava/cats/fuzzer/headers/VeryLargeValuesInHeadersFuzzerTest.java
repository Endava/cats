package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class VeryLargeValuesInHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private ProcessingArguments processingArguments;

    private VeryLargeValuesInHeadersFuzzer veryLargeValuesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        veryLargeValuesInHeadersFuzzer = new VeryLargeValuesInHeadersFuzzer(serviceCaller, testCaseListener, processingArguments);
    }

    @Test
    void givenANewLargeValuesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLargeValuesInHeadersFuzzer() {
        Assertions.assertThat(veryLargeValuesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeValuesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(veryLargeValuesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
    }

    @Test
    void shouldGetPayloadSize() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20000);

        Assertions.assertThat(veryLargeValuesInHeadersFuzzer.fuzzStrategy()).hasSize(1);
        Assertions.assertThat(veryLargeValuesInHeadersFuzzer.fuzzStrategy().get(0).getData()).hasSize(20000);
    }
}
