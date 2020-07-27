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
class TrailingSpacesInHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private TrailingSpacesInHeadersFuzzer trailingSpacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingSpacesInHeadersFuzzer = new TrailingSpacesInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenANewTrailingSpacesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTrailingSpacesInHeadersFuzzer() {
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.fuzzStrategy().name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.fuzzStrategy().getData()).isEqualTo("    ");

    }
}
