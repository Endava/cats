package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
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
        HeadersIteratorExecutor headersIteratorExecutor = new HeadersIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilterArguments.class));
        abugidasInHeadersFuzzer = new AbugidasInHeadersFuzzer(headersIteratorExecutor);
    }

    @Test
    void shouldHaveAllMethodsOverridden() {
        Assertions.assertThat(abugidasInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(abugidasInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(abugidasInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(abugidasInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isFalse();
    }

    @Test
    void shouldGetZalgoTextAsPayload() {
        Assertions.assertThat(abugidasInHeadersFuzzer.getFuzzerContext().getFuzzStrategy()).hasSize(2);
        Assertions.assertThat(abugidasInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().getData()).isEqualTo("జ్ఞ\u200Cా");
    }
}