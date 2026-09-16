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
        statistics.increaseSkippedFromReporting("/customers");
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
                "Quality gate", "PASSED", "Tests Reported", "1",
                "Authentication errors", "I/O errors",
                "Random seed", "12345", "Runtime resource reuse", "Enabled", "Configured stop limits",
                "Processing Limitations", "GET /customers", "Could not resolve a request schema reference",
                "Response", "201", "Time", "12ms")
                .doesNotContain("Execution accounting", "Skipped tests", "HTTP requests sent",
                        "Results included in report", "Omitted by reporting rules");
        Assertions.assertThat(json).contains(
                "\"totalRequests\": \"2\"", "\"totalTests\": \"1\"",
                "\"skippedFromReporting\": \"1\"",
                "\"authErrors\": 1", "\"ioErrors\": 1", "\"runStatus\": \"LIMIT_REACHED\"",
                "\"qualityGatePassed\": true", "\"randomSeed\": \"12345\"",
                "\"successfulResourceReuseEnabled\": true", "\"stopAfterTests\": \"25\"",
                "Could not resolve a request schema reference")
                .doesNotContain("\"completedTests\":", "\"skipped\":");
    }

    @Test
    void shouldWriteReplayEnvironmentGuidance() throws Exception {
        ReportingArguments reportingArguments = new ReportingArguments();
        reportingArguments.registerReplayEnvironmentVariable("Authorization");
        reportingArguments.registerReplayEnvironmentVariable("ACCESS_TOKEN");
        ProcessingArguments processingArguments = Mockito.mock(ProcessingArguments.class);
        StopArguments stopArguments = Mockito.mock(StopArguments.class);
        TestCaseExporterHtmlOnly exporter = Mockito.spy(new TestCaseExporterHtmlOnly(reportingArguments,
                new CatsGlobalContext(), processingArguments, stopArguments));
        Mockito.doReturn(reportDirectory.resolve("replay.env.example")).when(exporter).replayEnvironmentExamplePath();
        exporter.appVersion = "test-version";
        exporter.initPath(reportDirectory.toString());

        exporter.writeSummary(List.of(summary()), new ExecutionStatisticsListener().snapshot(true, "Default: fail on any error"));

        Assertions.assertThat(Files.readString(reportDirectory.resolve("replay.env.example")))
                .contains("Authorization=", "ACCESS_TOKEN=").doesNotContain("secret");
        Assertions.assertThat(Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML)))
                .doesNotContain("Replay Environment Variables", "Authorization", "--envFile");
        Assertions.assertThat(Files.readString(reportDirectory.resolve("cats-summary-report.json")))
                .doesNotContain("replayEnvironmentVariables", "Authorization");
    }

    @Test
    void shouldNotOverwriteExistingReplayEnvironmentExample() throws Exception {
        ReportingArguments reportingArguments = new ReportingArguments();
        reportingArguments.registerReplayEnvironmentVariable("Authorization");
        Path existingExample = reportDirectory.resolve("replay.env.example");
        Files.writeString(existingExample, "EXISTING=value\n");
        TestCaseExporterHtmlOnly exporter = Mockito.spy(new TestCaseExporterHtmlOnly(reportingArguments,
                new CatsGlobalContext(), Mockito.mock(ProcessingArguments.class), Mockito.mock(StopArguments.class)));
        Mockito.doReturn(existingExample).when(exporter).replayEnvironmentExamplePath();
        exporter.appVersion = "test-version";
        exporter.initPath(reportDirectory.resolve("report").toString());

        exporter.writeSummary(List.of(summary()), new ExecutionStatisticsListener().snapshot(true, "Default: fail on any error"));

        Assertions.assertThat(Files.readString(existingExample)).isEqualTo("EXISTING=value\n");
    }

    @Test
    void shouldNotIncludeExecutionAccountingForOrdinaryRuns() throws Exception {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        ProcessingArguments processingArguments = Mockito.mock(ProcessingArguments.class);
        StopArguments stopArguments = Mockito.mock(StopArguments.class);
        ExecutionStatisticsListener statistics = new ExecutionStatisticsListener();

        Mockito.when(reportingArguments.getOutputReportFolder()).thenReturn(reportDirectory.toString());
        Mockito.when(reportingArguments.getMaskedHeaders()).thenReturn(Set.of());
        statistics.increaseSuccess("/customers");
        statistics.increaseRequestsAttempted();

        TestCaseExporterHtmlOnly exporter = new TestCaseExporterHtmlOnly(reportingArguments,
                new CatsGlobalContext(), processingArguments, stopArguments);
        exporter.appVersion = "test-version";
        exporter.initPath(reportDirectory.toString());
        exporter.writeSummary(List.of(summary()), statistics.snapshot(true, "Default: fail on any error"));

        String html = Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML));

        Assertions.assertThat(html)
                .contains("Tests Reported", "Run outcome", "Quality gate", "Execution diagnostics", "Reproduction")
                .doesNotContain("Execution accounting", "Tests completed", "HTTP requests sent",
                        "Results included in report", "Skipped tests", "Omitted by reporting rules");
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
