package com.endava.cats.fuzzer.headers;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class LargeValuesInHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private LargeValuesInHeadersFuzzer largeValuesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        largeValuesInHeadersFuzzer = new LargeValuesInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenANewLargeValuesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLargeValuesInHeadersFuzzer() {
        Assertions.assertThat(largeValuesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(largeValuesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(largeValuesInHeadersFuzzer.fuzzStrategy().name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(largeValuesInHeadersFuzzer.fuzzStrategy().getData()).hasSizeGreaterThan(1500);

    }
}
