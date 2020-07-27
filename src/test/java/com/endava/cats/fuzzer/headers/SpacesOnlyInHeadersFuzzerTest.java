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
class SpacesOnlyInHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private SpacesOnlyInHeadersFuzzer spacesOnlyInHeadersFuzzer;

    @BeforeEach
    void setup() {
        spacesOnlyInHeadersFuzzer = new SpacesOnlyInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenANewSpacesOnlyInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheSpacesOnlyInHeadersFuzzer() {
        Assertions.assertThat(spacesOnlyInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(spacesOnlyInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(spacesOnlyInHeadersFuzzer.fuzzStrategy().name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(spacesOnlyInHeadersFuzzer.fuzzStrategy().getData()).isEqualTo("   ");

    }
}
