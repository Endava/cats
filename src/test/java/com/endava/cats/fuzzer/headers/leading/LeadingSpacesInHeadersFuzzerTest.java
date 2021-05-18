package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class LeadingSpacesInHeadersFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private LeadingSpacesInHeadersFuzzer leadingSpacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        leadingSpacesInHeadersFuzzer = new LeadingSpacesInHeadersFuzzer(catsUtil, serviceCaller, testCaseListener);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo(" ");
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0009");
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.fuzzStrategy()).hasSize(2);

        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getExpectedHttpForOptionalHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
    }
}
