package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.headers.only.ControlCharsOnlyInHeadersFuzzer;
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
class ControlCharsOnlyInHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private ControlCharsOnlyInHeadersFuzzer controlCharsOnlyInHeadersFuzzer;

    @BeforeEach
    void setup() {
        controlCharsOnlyInHeadersFuzzer = new ControlCharsOnlyInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenANewSpacesOnlyInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheSpacesOnlyInHeadersFuzzer() {
        Assertions.assertThat(controlCharsOnlyInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(controlCharsOnlyInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(controlCharsOnlyInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(controlCharsOnlyInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");

    }
}
