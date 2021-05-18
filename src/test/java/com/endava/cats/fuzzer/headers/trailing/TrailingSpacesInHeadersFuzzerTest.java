package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class TrailingSpacesInHeadersFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private TrailingSpacesInHeadersFuzzer trailingSpacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingSpacesInHeadersFuzzer = new TrailingSpacesInHeadersFuzzer(catsUtil, serviceCaller, testCaseListener);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo(" ");
    }
}
