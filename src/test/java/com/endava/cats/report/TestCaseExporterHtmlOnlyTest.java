package com.endava.cats.report;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@QuarkusTest
class TestCaseExporterHtmlOnlyTest {

    @TempDir
    Path reportDirectory;

    @Test
    void shouldIncludeRunDetailsAndExecutionDiagnosticsInHtmlAndJsonReports() throws Exception {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        ProcessingArguments processingArguments = Mockito.mock(ProcessingArguments.class);
        StopArguments stopArguments = Mockito.mock(StopArguments.class);
        CatsGlobalContext globalContext = new CatsGlobalContext();
        ExecutionStatisticsListener statistics = new ExecutionStatisticsListener();

        Mockito.when(reportingArguments.getOutputReportFolder()).thenReturn(reportDirectory.toString());
        Mockito.when(reportingArguments.getMaskedHeaders()).thenReturn(Set.of());
        Mockito.when(processingArguments.isReuseSuccessfulResources()).thenReturn(true);
        Mockito.when(stopArguments.isAnyStopConditionProvided()).thenReturn(true);
        Mockito.when(stopArguments.getStopAfterMutations()).thenReturn(25L);

        statistics.increaseSuccess("/customers");
        statistics.increaseSkipped();
        statistics.increaseSkippedFromReporting("/customers");
        statistics.increaseCompletedTests();
        statistics.increaseCompletedTests();
        statistics.increaseRequestsAttempted();
        statistics.increaseRequestsAttempted();
        statistics.increaseAuthErrors();
        statistics.increaseIoErrors();
        statistics.markLimitReached("Execution stopped after reaching --stopAfterTests (25 tests)");
        CatsRandom.initRandom(12345L);
        MDC.put(CatsGlobalContext.HTTP_METHOD, "GET");
        MDC.put(CatsGlobalContext.CONTRACT_PATH, "/customers");
        globalContext.recordError("Could not resolve a request schema reference");
        MDC.clear();

        ExecutionSummary executionSummary = statistics.snapshot(true, "Default: fail on any error");
        TestCaseExporterHtmlOnly exporter = new TestCaseExporterHtmlOnly(reportingArguments, globalContext,
                processingArguments, stopArguments);
        exporter.appVersion = "test-version";
        exporter.initPath(reportDirectory.toString());
        exporter.writeSummary(List.of(summary()), executionSummary);

        String html = Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML));
        String json = Files.readString(reportDirectory.resolve("cats-summary-report.json"));

        Assertions.assertThat(html).contains(
                "Run Details", "Limit reached", "Execution stopped after reaching --stopAfterTests (25 tests)",
                "Quality gate", "PASSED", "Tests Completed", "2", "HTTP requests sent", "2",
                "Results included in report", "1",
                "Omitted by reporting rules", "Skipped tests (total)", "Authentication errors", "I/O errors",
                "Random seed", "12345", "Runtime resource reuse", "Enabled", "Configured stop limits",
                "Processing Limitations", "GET /customers", "Could not resolve a request schema reference",
                "Response", "201", "Time", "12ms");
        Assertions.assertThat(json).contains(
                "\"completedTests\": \"2\"", "\"totalRequests\": \"2\"", "\"totalTests\": \"1\"",
                "\"skippedFromReporting\": \"1\"", "\"skipped\": 1",
                "\"authErrors\": 1", "\"ioErrors\": 1", "\"runStatus\": \"LIMIT_REACHED\"",
                "\"qualityGatePassed\": true", "\"randomSeed\": \"12345\"",
                "\"successfulResourceReuseEnabled\": true", "\"stopAfterTests\": \"25\"",
                "Could not resolve a request schema reference");
    }

    @Test
    void shouldHideRedundantReportAccountingForOrdinaryRuns() throws Exception {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        ProcessingArguments processingArguments = Mockito.mock(ProcessingArguments.class);
        StopArguments stopArguments = Mockito.mock(StopArguments.class);
        ExecutionStatisticsListener statistics = new ExecutionStatisticsListener();

        Mockito.when(reportingArguments.getOutputReportFolder()).thenReturn(reportDirectory.toString());
        Mockito.when(reportingArguments.getMaskedHeaders()).thenReturn(Set.of());
        statistics.increaseSuccess("/customers");
        statistics.increaseCompletedTests();
        statistics.increaseRequestsAttempted();

        TestCaseExporterHtmlOnly exporter = new TestCaseExporterHtmlOnly(reportingArguments,
                new CatsGlobalContext(), processingArguments, stopArguments);
        exporter.appVersion = "test-version";
        exporter.initPath(reportDirectory.toString());
        exporter.writeSummary(List.of(summary()), statistics.snapshot(true, "Default: fail on any error"));

        String html = Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML));

        Assertions.assertThat(html)
                .contains("Tests completed", "HTTP requests sent", "Skipped tests (total)")
                .doesNotContain("Results included in report", "Omitted by reporting rules");
    }

    private static CatsTestCaseSummary summary() {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setTestId("Test 1");
        testCase.setScenario("A report scenario");
        testCase.setResult("success");
        testCase.setResultReason("Response matches expectations");
        testCase.setResultDetails("");
        testCase.setFuzzer("HappyPathFuzzer");
        testCase.setContractPath("/customers");
        testCase.setRequest(CatsRequest.builder().httpMethod("POST").build());
        testCase.setResponse(CatsResponse.builder().responseCode(201).responseTimeInMs(12).body("{}").build());
        return CatsTestCaseSummary.fromCatsTestCase(testCase);
    }

}
