package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.headers.trailing.TrailingControlCharsInHeadersTrimValidateFuzzer;
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
class TrailingControlCharsInHeadersTrimValidateFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private TrailingControlCharsInHeadersTrimValidateFuzzer trailingControlCharsInHeadersTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        trailingControlCharsInHeadersTrimValidateFuzzer = new TrailingControlCharsInHeadersTrimValidateFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenANewTrailingSpacesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTrailingSpacesInHeadersFuzzer() {
        Assertions.assertThat(trailingControlCharsInHeadersTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingControlCharsInHeadersTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(trailingControlCharsInHeadersTrimValidateFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingControlCharsInHeadersTrimValidateFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");

    }
}
