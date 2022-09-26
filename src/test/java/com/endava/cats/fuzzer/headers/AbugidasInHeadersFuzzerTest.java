package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class AbugidasInHeadersFuzzerTest {
    private AbugidasInHeadersFuzzer abugidasInHeadersFuzzer;

    @BeforeEach
    void setup() {
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        HeadersIteratorExecutor headersIteratorExecutor = new HeadersIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class));
        abugidasInHeadersFuzzer = new AbugidasInHeadersFuzzer(headersIteratorExecutor);
    }

    @Test
    void shouldHaveAllMethodsOverridden() {
        Assertions.assertThat(abugidasInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(abugidasInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(abugidasInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(abugidasInHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldGetZalgoTextAsPayload() {
        Assertions.assertThat(abugidasInHeadersFuzzer.fuzzStrategy()).hasSize(2);
        Assertions.assertThat(abugidasInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo("జ్ఞ\u200Cా");
    }
}