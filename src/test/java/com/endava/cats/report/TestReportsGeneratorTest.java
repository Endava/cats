package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.RunOutcome;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.SensitiveDataPolicy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@QuarkusTest
class TestReportsGeneratorTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldShareOneExecutionSummaryWithEveryReportConsumer() {
        TestCaseExporter htmlExporter = Mockito.mock(TestCaseExporter.class);
        TestCaseExporter junitExporter = Mockito.mock(TestCaseExporter.class);
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        ExecutionSummary summary = new ExecutionSummary(5, 4, 3, 0, 1, 1, 0, 0,
                Map.of(200, 3), Map.of("/orders", 1L), false, "fail on errors", RunOutcome.completed());

        Mockito.when(exporters.stream()).thenReturn(Stream.of(htmlExporter, junitExporter));
        Mockito.when(reportingArguments.getReportFormat()).thenReturn(List.of(
                ReportingArguments.ReportFormat.HTML_ONLY, ReportingArguments.ReportFormat.JUNIT));
        Mockito.when(htmlExporter.reportFormat()).thenReturn(ReportingArguments.ReportFormat.HTML_ONLY);
        Mockito.when(junitExporter.reportFormat()).thenReturn(ReportingArguments.ReportFormat.JUNIT);
        TestReportsGenerator generator = new TestReportsGenerator(exporters, reportingArguments);

        generator.writeSummary(List.of(), summary);
        generator.printExecutionDetails(summary);

        Mockito.verify(htmlExporter).writeSummary(List.of(), summary);
        Mockito.verify(junitExporter).writeSummary(List.of(), summary);
        Mockito.verify(htmlExporter).printExecutionDetails(summary);
        Mockito.verify(junitExporter, Mockito.never()).printExecutionDetails(Mockito.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldChoosePrimaryExporterByRequestedFormatRatherThanCdiOrder() {
        TestCaseExporter junitExporter = Mockito.mock(TestCaseExporter.class);
        TestCaseExporter htmlExporter = Mockito.mock(TestCaseExporter.class);
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        CatsTestCase testCase = new CatsTestCase();

        Mockito.when(exporters.stream()).thenReturn(Stream.of(junitExporter, htmlExporter));
        Mockito.when(reportingArguments.getReportFormat()).thenReturn(List.of(
                ReportingArguments.ReportFormat.HTML_ONLY, ReportingArguments.ReportFormat.JUNIT));
        Mockito.when(htmlExporter.reportFormat()).thenReturn(ReportingArguments.ReportFormat.HTML_ONLY);
        Mockito.when(junitExporter.reportFormat()).thenReturn(ReportingArguments.ReportFormat.JUNIT);

        TestReportsGenerator generator = new TestReportsGenerator(exporters, reportingArguments);
        generator.writeTestCase(testCase);

        Mockito.verify(htmlExporter).writeTestCase(testCase);
        Mockito.verify(junitExporter, Mockito.never()).writeTestCase(Mockito.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSanitizeSensitiveDataBeforeWritingTestCase() {
        TestCaseExporter exporter = Mockito.mock(TestCaseExporter.class);
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        ReportingArguments reportingArguments = new ReportingArguments();
        Mockito.when(exporters.stream()).thenReturn(Stream.of(exporter));
        Mockito.when(exporter.reportFormat()).thenReturn(ReportingArguments.ReportFormat.HTML_JS);
        CatsTestCase testCase = new CatsTestCase();
        testCase.setRequest(CatsRequest.builder()
                .headers(List.of(new KeyValuePair<>("Authorization", "Bearer secret")))
                .url("https://service.test/items?access_token=secret")
                .build());
        testCase.setFullRequestPath(testCase.getRequest().getUrl());
        testCase.setPath(testCase.getRequest().getUrl());
        testCase.setContractPath(testCase.getRequest().getUrl());
        testCase.setResponse(CatsResponse.builder()
                .headers(List.of(new KeyValuePair<>("Set-Cookie", "session=secret")))
                .build());

        TestReportsGenerator generator = new TestReportsGenerator(exporters, reportingArguments);
        generator.writeTestCase(testCase);

        Assertions.assertThat(testCase.getRequest().getHeaders().getFirst().getValue()).isEqualTo("$$Authorization");
        Assertions.assertThat(testCase.getRequest().getUrl()).contains("access_token=$$ACCESS_TOKEN").doesNotContain("secret");
        Assertions.assertThat(testCase.getFullRequestPath()).doesNotContain("secret");
        Assertions.assertThat(testCase.getPath()).doesNotContain("secret");
        Assertions.assertThat(testCase.getContractPath()).doesNotContain("secret");
        Assertions.assertThat(testCase.getResponse().getHeaders().getFirst().getValue()).isEqualTo(SensitiveDataPolicy.REDACTED);
        Assertions.assertThat(reportingArguments.getReplayEnvironmentVariables())
                .containsExactlyInAnyOrder("Authorization", "ACCESS_TOKEN");
        Mockito.verify(exporter).writeTestCase(testCase);
    }
}
