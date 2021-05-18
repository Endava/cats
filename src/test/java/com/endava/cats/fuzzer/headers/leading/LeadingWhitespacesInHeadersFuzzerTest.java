package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class LeadingWhitespacesInHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private LeadingWhitespacesInHeadersFuzzer leadingWhitespacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        leadingWhitespacesInHeadersFuzzer = new LeadingWhitespacesInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo("\u1680");
    }
}
