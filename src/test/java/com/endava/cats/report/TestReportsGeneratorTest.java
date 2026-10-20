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

    @Test
    void shouldCreateSummaryReportContextAcrossAllOutcomesAndBranches() {
        com.endava.cats.model.CatsTestReport failingReport = com.endava.cats.model.CatsTestReport.builder()
                .warnings(2).success(7).errors(1).reportedResults(10).requestsAttempted(10)
                .authErrors(1).ioErrors(2).timestamp("2026-10-19").executionTime(15).catsVersion("14.0.1")
                .runStatus("LIMIT_REACHED").runStatusDetails("stopped").qualityGatePassed(false)
                .qualityGateDescription("failed gate").randomSeed(42L).successfulResourceReuseEnabled(true)
                .stopAfterTests(100).stopAfterErrors(5).stopAfterTimeInSec(60)
                .processingErrors(List.of(
                        new com.endava.cats.model.ProcessingError(null, null, "global err"),
                        new com.endava.cats.model.ProcessingError("/items", "", "path err"),
                        new com.endava.cats.model.ProcessingError("", "POST", "method err"),
                        new com.endava.cats.model.ProcessingError("/items", "GET", "full err")))
                .testCases(List.of())
                .build();
        com.endava.cats.model.CatsConfiguration config = new com.endava.cats.model.CatsConfiguration(
                "14.0.1", "openapi.yaml", "http://localhost:8080", List.of(com.endava.cats.http.HttpMethod.GET), 5, 10L, 2, 4);

        for (RunOutcome outcome : List.of(
                RunOutcome.completed(),
                new RunOutcome(RunOutcome.Status.LIMIT_REACHED, "limit"),
                new RunOutcome(RunOutcome.Status.CANCELLED, "cancelled"),
                new RunOutcome(RunOutcome.Status.FAILED, "failed"))) {
            ExecutionSummary execSummary = new ExecutionSummary(10, 10, 7, 0, 2, 1, 1, 2,
                    Map.of(100, 1, 200, 7, 302, 1, 404, 1, 500, 1, 600, 1),
                    Map.of("/items", 1L), false, "failed gate", outcome);
            SummaryReportContext ctx = SummaryReportContext.create(failingReport, execSummary, config, true, "macOS", 12.34);
            assertPopulatedSummaryReportContext(ctx);
        }

        com.endava.cats.model.CatsTestReport emptyReport = com.endava.cats.model.CatsTestReport.builder()
                .reportedResults(0).runStatus("COMPLETED").qualityGatePassed(true)
                .successfulResourceReuseEnabled(false).processingErrors(List.of()).testCases(List.of())
                .build();
        ExecutionSummary emptySummary = new ExecutionSummary(0, 0, 0, 0, 0, 0, 0, 0,
                Map.of(), Map.of(), true, "passed", RunOutcome.completed());
        SummaryReportContext emptyCtx = SummaryReportContext.create(emptyReport, emptySummary, null, false, "Linux", 0.0);
        Assertions.assertThat(emptyCtx.getQualityGateStatus()).isEqualTo("PASSED");
        Assertions.assertThat(emptyCtx.getQualityGateResult()).isEqualTo("success");
        Assertions.assertThat(emptyCtx.getResourceReuseStatus()).isEqualTo("Disabled");
        Assertions.assertThat(emptyCtx.isHasStopLimits()).isFalse();
        Assertions.assertThat(emptyCtx.isHasProcessingErrors()).isFalse();
        Assertions.assertThat(emptyCtx.isHasResponseCodes()).isFalse();
        Assertions.assertThat(emptyCtx.isHasFailingPaths()).isFalse();
        Assertions.assertThat(emptyCtx.getSuccessPercentage()).isZero();
    }

    private static void assertPopulatedSummaryReportContext(SummaryReportContext ctx) {
        Assertions.assertThat(ctx.getQualityGateStatus()).isEqualTo("FAILED");
        Assertions.assertThat(ctx.getQualityGateResult()).isEqualTo("error");
        Assertions.assertThat(ctx.getResourceReuseStatus()).isEqualTo("Enabled");
        Assertions.assertThat(ctx.isHasStopLimits()).isTrue();
        Assertions.assertThat(ctx.getStopLimits()).hasSize(3);
        Assertions.assertThat(ctx.isHasProcessingErrors()).isTrue();
        Assertions.assertThat(ctx.getProcessingErrors()).extracting(SummaryReportContext.ProcessingErrorEntry::operation)
                .containsExactly("Global", "/items", "POST", "GET /items");
        Assertions.assertThat(ctx.isHasResponseCodes()).isTrue();
        Assertions.assertThat(ctx.getResponseCodeDistribution()).extracting(SummaryReportContext.ResponseCodeEntry::family)
                .containsExactly("other", "2xx", "3xx", "4xx", "5xx", "other");
        Assertions.assertThat(ctx.isHasFailingPaths()).isTrue();
        Assertions.assertThat(ctx.getContractName()).isEqualTo("openapi.yaml");
        Assertions.assertThat(ctx.getBaseUrl()).isEqualTo("http://localhost:8080");
        Assertions.assertThat(ctx.getHttpMethods()).containsExactly("get");
        Assertions.assertThat(ctx.getFuzzers()).isEqualTo(5);
        Assertions.assertThat(ctx.getTotalFuzzers()).isEqualTo(10);
        Assertions.assertThat(ctx.getPaths()).isEqualTo(2);
        Assertions.assertThat(ctx.getTotalPaths()).isEqualTo(4);
        assertPopulatedSummaryMetrics(ctx);
    }

    private static void assertPopulatedSummaryMetrics(SummaryReportContext ctx) {
        Assertions.assertThat(ctx.getWarnings()).isEqualTo("2");
        Assertions.assertThat(ctx.getSuccess()).isEqualTo("7");
        Assertions.assertThat(ctx.getErrors()).isEqualTo("1");
        Assertions.assertThat(ctx.getErrorsJunit()).isEqualTo("0");
        Assertions.assertThat(ctx.getFailuresJunit()).isEqualTo("0");
        Assertions.assertThat(ctx.getTestsReported()).isEqualTo("10");
        Assertions.assertThat(ctx.getReportedResults()).isEqualTo("10");
        Assertions.assertThat(ctx.getAuthErrors()).isEqualTo("1");
        Assertions.assertThat(ctx.getIoErrors()).isEqualTo("2");
        Assertions.assertThat(ctx.getTimestamp()).isEqualTo("2026-10-19");
        Assertions.assertThat(ctx.getTimestampIso()).isNotBlank();
        Assertions.assertThat(ctx.getTestCases()).isEmpty();
        Assertions.assertThat(ctx.getTestSuites()).isEmpty();
        Assertions.assertThat(ctx.getExecution()).isNotBlank();
        Assertions.assertThat(ctx.getTime()).isEqualTo(15);
        Assertions.assertThat(ctx.getVersion()).isEqualTo("14.0.1");
        Assertions.assertThat(ctx.isJavascript()).isTrue();
        Assertions.assertThat(ctx.getOs()).isEqualTo("macOS");
        Assertions.assertThat(ctx.getAverageResponseTime()).isNotBlank();
        Assertions.assertThat(ctx.getRunStatus()).isEqualTo("Limit reached");
        Assertions.assertThat(ctx.getRunStatusDetails()).isEqualTo("stopped");
        Assertions.assertThat(ctx.getRunStatusResult()).isNotBlank();
        Assertions.assertThat(ctx.getQualityGateDescription()).isEqualTo("failed gate");
        Assertions.assertThat(ctx.getRandomSeed()).isEqualTo(42L);
        Assertions.assertThat(ctx.getWarnPercentage()).isEqualTo(20.0);
        Assertions.assertThat(ctx.getErrorPercentage()).isEqualTo(10.0);
        Assertions.assertThat(ctx.getSuccessPercentage()).isEqualTo(70.0);
        Assertions.assertThat(ctx.getGroupedTestCases()).isEmpty();
        Assertions.assertThat(ctx.getTopFailingPaths()).hasSize(1);
    }
}
