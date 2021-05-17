package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.headers.leading.LeadingControlCharsInHeadersTrimValidateFuzzer;
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
class LeadingControlCharsInHeadersTrimValidateFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private LeadingControlCharsInHeadersTrimValidateFuzzer leadingControlCharsInHeadersTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        leadingControlCharsInHeadersTrimValidateFuzzer = new LeadingControlCharsInHeadersTrimValidateFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void givenANewLeadingSpacesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingSpacesInHeadersFuzzer() {
        Assertions.assertThat(leadingControlCharsInHeadersTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingControlCharsInHeadersTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(leadingControlCharsInHeadersTrimValidateFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingControlCharsInHeadersTrimValidateFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");

    }
}
