package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.headers.only.ControlCharsOnlyInHeadersTrimValidateFuzzer;
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
class ControlCharsOnlyInHeadersTrimValidateFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private ControlCharsOnlyInHeadersTrimValidateFuzzer controlCharsOnlyInHeadersTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        controlCharsOnlyInHeadersTrimValidateFuzzer = new ControlCharsOnlyInHeadersTrimValidateFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenANewSpacesOnlyInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheSpacesOnlyInHeadersFuzzer() {
        Assertions.assertThat(controlCharsOnlyInHeadersTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(controlCharsOnlyInHeadersTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(controlCharsOnlyInHeadersTrimValidateFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(controlCharsOnlyInHeadersTrimValidateFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");

    }
}
