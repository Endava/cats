package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class OnlyWhitespacesInHeadersFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private OnlyWhitespacesInHeadersFuzzer onlyWhitespacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlyWhitespacesInHeadersFuzzer = new OnlyWhitespacesInHeadersFuzzer(catsUtil, serviceCaller, testCaseListener);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo("\u1680");
    }
}
