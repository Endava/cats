package com.endava.cats.fuzzer.headers;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AbugidasCharsInHeadersFuzzerTest {
    private AbugidasCharsInHeadersFuzzer abugidasCharsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        abugidasCharsInHeadersFuzzer = new AbugidasCharsInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void shouldHaveAllMethodsOverridden() {
        Assertions.assertThat(abugidasCharsInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(abugidasCharsInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(abugidasCharsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(abugidasCharsInHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldGetZalgoTextAsPayload() {
        Assertions.assertThat(abugidasCharsInHeadersFuzzer.fuzzStrategy()).hasSize(2);
        Assertions.assertThat(abugidasCharsInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo("జ్ఞ\u200Cా");
    }
}