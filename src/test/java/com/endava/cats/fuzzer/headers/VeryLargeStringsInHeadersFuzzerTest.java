package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ProcessingArguments;
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
class VeryLargeStringsInHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private ProcessingArguments processingArguments;

    private VeryLargeStringsInHeadersFuzzer veryLargeStringsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        HeadersIteratorExecutor headersIteratorExecutor = new HeadersIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilterArguments.class));
        veryLargeStringsInHeadersFuzzer = new VeryLargeStringsInHeadersFuzzer(headersIteratorExecutor, processingArguments);
    }

    @Test
    void givenANewLargeValuesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLargeValuesInHeadersFuzzer() {
        Assertions.assertThat(veryLargeStringsInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(veryLargeStringsInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(veryLargeStringsInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().name()).isEqualTo(FuzzingStrategy.replace().name());
    }

    @Test
    void shouldGetPayloadSize() {
        Mockito.when(processingArguments.getLargeStringsSize()).thenReturn(20000);

        Assertions.assertThat(veryLargeStringsInHeadersFuzzer.getFuzzerContext().getFuzzStrategy()).hasSize(1);
        Assertions.assertThat(veryLargeStringsInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().getData().toString()).hasSize(20000);
    }

    @Test
    void shouldNotMatchResponse() {
        Assertions.assertThat(veryLargeStringsInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isFalse();
    }

    @Test
    void shouldNotMatchContentType() {
        Assertions.assertThat(veryLargeStringsInHeadersFuzzer.getFuzzerContext().isMatchResponseContentType()).isFalse();
    }
}
