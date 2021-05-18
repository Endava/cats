package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class OnlySpacesInHeadersFuzzerTest {

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private OnlySpacesInHeadersFuzzer onlySpacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlySpacesInHeadersFuzzer = new OnlySpacesInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(onlySpacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlySpacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlySpacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo(" ");
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getExpectedHttpForOptionalHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
    }
}
