package com.endava.cats.fuzzer.headers.only;

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
class OnlyControlCharsInHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private OnlyControlCharsInHeadersFuzzer onlyControlCharsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlyControlCharsInHeadersFuzzer = new OnlyControlCharsInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenANewSpacesOnlyInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheSpacesOnlyInHeadersFuzzer() {
        Assertions.assertThat(onlyControlCharsInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyControlCharsInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyControlCharsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlyControlCharsInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");

    }
}
