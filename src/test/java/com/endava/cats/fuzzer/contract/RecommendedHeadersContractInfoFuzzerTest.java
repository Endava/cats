package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseExporterHtmlJs;
import com.endava.cats.report.TestCaseListener;
import com.google.common.collect.Sets;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import javax.enterprise.inject.Instance;
import java.util.stream.Stream;

@QuarkusTest
class RecommendedHeadersContractInfoFuzzerTest {
    private TestCaseListener testCaseListener;
    private RecommendedHeadersContractInfoFuzzer recommendedHeadersContractInfoFuzzer;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        TestCaseExporter exporter = Mockito.mock(TestCaseExporterHtmlJs.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(exporter));
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        recommendedHeadersContractInfoFuzzer = new RecommendedHeadersContractInfoFuzzer(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"X-Trace-Id", "XTraceid", "X-CorrelationId", "X-APP-Correlation_Id"})
    void shouldReportInfo(String header) {
        CatsHeader catsHeader = CatsHeader.builder().name(header).build();
        FuzzingData data = FuzzingData.builder().headers(Sets.newHashSet(catsHeader)).method(HttpMethod.POST).build();
        recommendedHeadersContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(),
                Mockito.eq("Path contains the recommended [TracedId/CorrelationId] headers for HTTP method {}"), Mockito.eq(HttpMethod.POST));
    }

    @ParameterizedTest
    @CsvSource({"X-Trac-Id", "XTracing", "X-Correlation", "X-APP-Correlation*Id"})
    void shouldReportError(String header) {
        CatsHeader catsHeader = CatsHeader.builder().name(header).build();
        FuzzingData data = FuzzingData.builder().headers(Sets.newHashSet(catsHeader)).method(HttpMethod.POST).build();
        recommendedHeadersContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(),
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