package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
class TracingHeadersLinterTest {
    private TestCaseListener testCaseListener;
    private TracingHeadersLinter recommendedHeadersContractInfoFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        recommendedHeadersContractInfoFuzzer = new TracingHeadersLinter(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"X-Trace-Id", "XTraceid", "X-CorrelationId", "X-APP-Correlation_Id"})
    void shouldReportInfo(String header) {
        CatsHeader catsHeader = CatsHeader.builder().name(header).build();
        FuzzingData data = FuzzingData.builder().headers(Set.of(catsHeader)).method(HttpMethod.POST).build();
        recommendedHeadersContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path contains the recommended [TracedId/CorrelationId] headers for HTTP method {}"), Mockito.eq(HttpMethod.POST));
    }

    @ParameterizedTest
    @CsvSource({"X-Trac-Id", "XTracing", "X-Correlation", "X-APP-Correlation*Id"})
    void shouldReportError(String header) {
        CatsHeader catsHeader = CatsHeader.builder().name(header).build();
        FuzzingData data = FuzzingData.builder().headers(Set.of(catsHeader)).method(HttpMethod.POST).build();
        recommendedHeadersContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.eq("Path does not contain the recommended [TracedId/CorrelationId] headers for HTTP method {}"), Mockito.eq(HttpMethod.POST));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(recommendedHeadersContractInfoFuzzer).hasToString(recommendedHeadersContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(recommendedHeadersContractInfoFuzzer.description()).isEqualTo("verifies that all OpenAPI contract paths contain recommended headers like: CorrelationId/TraceId, etc. ");
    }

}