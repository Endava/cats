package com.endava.cats.report;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.Problem;
import com.endava.cats.report.problem.ProblemCatalog;
import com.endava.cats.report.problem.ResponseSchemaNames;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@QuarkusTest
class TestCaseExporterHtmlOnlyTest {

    @TempDir
    Path reportDirectory;

    @Test
    void shouldRejectNullSummaryListsBeforeWritingAReport() throws Exception {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        TestCaseExporterHtmlOnly exporter = new TestCaseExporterHtmlOnly(reportingArguments,
                new CatsGlobalContext(), Mockito.mock(ProcessingArguments.class), Mockito.mock(StopArguments.class));
        exporter.initPath(reportDirectory.toString());
        ExecutionSummary executionSummary = new ExecutionStatisticsListener().snapshot(false, "fail on errors");

        Assertions.assertThatThrownBy(() -> exporter.writeSummary(null, executionSummary))
                .isInstanceOf(NullPointerException.class);
        try (var files = Files.list(reportDirectory)) {
            Assertions.assertThat(files).isEmpty();
        }
    }

    @Test
    void shouldIgnoreNullSummariesWhenWritingProblemsJs() throws Exception {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        Mockito.when(reportingArguments.getOutputReportFolder()).thenReturn(reportDirectory.toString());
        TestCaseExporterHtmlJs exporter = new TestCaseExporterHtmlJs(reportingArguments,
                new CatsGlobalContext(), Mockito.mock(ProcessingArguments.class), Mockito.mock(StopArguments.class));
        exporter.initPath(reportDirectory.toString());
        CatsTestCaseSummary error = ProblemReportFixtures.summaries(ProblemReportFixtures.injectionRun()).getFirst();
        List<Problem> problems = ProblemCatalog.build(List.of(error), new ResponseSchemaNames(null));

        exporter.writeProblemsJs(ProblemPage.of(problems, List.of(error), true), CatsTestReport.builder().testCases(List.of(error)).problems(problems).build());
        String expected = Files.readString(reportDirectory.resolve(TestCaseExporter.PROBLEMS_JS));
        List<CatsTestCaseSummary> withNulls = Arrays.asList(null, error, null);
        exporter.writeProblemsJs(ProblemPage.of(problems, withNulls, true), CatsTestReport.builder().testCases(withNulls).problems(problems).build());

        Assertions.assertThat(Files.readString(reportDirectory.resolve(TestCaseExporter.PROBLEMS_JS))).isEqualTo(expected)
                .contains("\"Test1\":", "\"fields\": [", "\"body.name\"");
    }

    @Test
    void shouldRenderProblemsStaticallyWithoutJavascriptPlaceholdersOrDanglingLinks() throws Exception {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        Mockito.when(reportingArguments.getOutputReportFolder()).thenReturn(reportDirectory.toString());
        Mockito.when(reportingArguments.getMaskedHeaders()).thenReturn(Set.of());
        TestCaseExporterHtmlOnly exporter = new TestCaseExporterHtmlOnly(reportingArguments,
                new CatsGlobalContext(), Mockito.mock(ProcessingArguments.class), Mockito.mock(StopArguments.class));
        exporter.appVersion = "test-version";
        exporter.initPath(reportDirectory.toString());
        List<CatsTestCase> run = ProblemReportFixtures.riskApiRun();
        run.forEach(exporter::writeTestCase);

        exporter.writeSummary(ProblemReportFixtures.summaries(run), new ExecutionStatisticsListener().snapshot(false, "fail on errors"));

        String html = Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML));
        String testPage = Files.readString(reportDirectory.resolve("Test4.html"));
        List<String> ids = Pattern.compile("\\sid=\"([^\"]+)\"").matcher(html).results().map(match -> match.group(1)).toList();
        Assertions.assertThat(html).doesNotContain("script.js", "problems.js", "data-view=", "problem-banner", "problem-view-tests", "{{")
                .contains("href=\"#problems-view\"", "href=\"#problems-by-operation\"", "href=\"#tests-view\"", "id=\"problems-by-operation\"",
                        "Grouped by:", "href=\"Test4.html\"");
        Assertions.assertThat(Pattern.compile("href=\"#([^\"]+)\"").matcher(html).results().map(match -> match.group(1)).distinct()).allMatch(ids::contains);
        Assertions.assertThat(ids).doesNotHaveDuplicates();
        Assertions.assertThat(testPage).doesNotContain("problems.js", "problem-banner", "script.js")
                .contains("Same result as the unmodified request");
        Assertions.assertThat(reportDirectory.resolve(TestCaseExporter.PROBLEMS_JS)).doesNotExist();
    }

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
                "Processing Limitations", "GET /customers", "Could not resolve a request schema reference")
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

    @Test
    void shouldExportBaselineAnnotationAndErrorTemplatesJsForJsFormats() throws Exception {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        ProcessingArguments processingArguments = Mockito.mock(ProcessingArguments.class);
        StopArguments stopArguments = Mockito.mock(StopArguments.class);
        Mockito.when(reportingArguments.getOutputReportFolder()).thenReturn(reportDirectory.toString());
        Mockito.when(reportingArguments.getMaskedHeaders()).thenReturn(Set.of());

        TestCaseExporterHtmlJs jsExporter = new TestCaseExporterHtmlJs(reportingArguments,
                new CatsGlobalContext(), processingArguments, stopArguments);
        jsExporter.appVersion = "test-version";
        jsExporter.initPath(reportDirectory.toString());

        CatsTestCase baselineCase = new CatsTestCase();
        baselineCase.setTestId("Test 3");
        baselineCase.setScenario("Happy path scenario");
        baselineCase.setResult("error");
        baselineCase.setResultReason("Unexpected response code: 500");
        baselineCase.setResultDetails("Unexpected 500");
        baselineCase.setFuzzer("HappyPath");
        baselineCase.setContractPath("/orders");
        baselineCase.setRequest(CatsRequest.builder().httpMethod("POST").payload("{}").build());
        baselineCase.setResponse(CatsResponse.builder().responseCode(500).responseTimeInMs(10).body("{\"message\":\"Database down\"}").build());

        CatsTestCase annotatedCase = new CatsTestCase();
        annotatedCase.setTestId("Test 12");
        annotatedCase.setScenario("Fuzzed scenario");
        annotatedCase.setResult("error");
        annotatedCase.setResultReason("Unexpected response code: 500");
        annotatedCase.setResultDetails("Unexpected 500");
        annotatedCase.setFuzzer("RemoveFields");
        annotatedCase.setContractPath("/orders");
        annotatedCase.setSameErrorAsBaselineTest("Test 3");
        annotatedCase.setSameErrorAsBaselineFuzzer("HappyPath");
        annotatedCase.setRequest(CatsRequest.builder().httpMethod("POST").payload("{}").build());
        annotatedCase.setResponse(CatsResponse.builder().responseCode(500).responseTimeInMs(10).body("{\"message\":\"Database down\"}").build());

        jsExporter.writeTestCase(annotatedCase);
        List<CatsTestCaseSummary> summaries = List.of(
                CatsTestCaseSummary.fromCatsTestCase(baselineCase),
                CatsTestCaseSummary.fromCatsTestCase(annotatedCase),
                summary());
        ExecutionSummary execSummary = new ExecutionStatisticsListener().snapshot(false, "Default: fail on any error");
        jsExporter.writeSummary(summaries, execSummary);

        String testJson = Files.readString(reportDirectory.resolve("Test12.json"));
        String testHtml = Files.readString(reportDirectory.resolve("Test12.html"));
        String summaryJson = Files.readString(reportDirectory.resolve("cats-summary-report.json"));
        String problemsJs = Files.readString(reportDirectory.resolve(TestCaseExporter.PROBLEMS_JS));
        String summaryHtml = Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML));

        Assertions.assertThat(testJson).contains(
                "\"sameErrorAsBaselineTest\": \"Test 3\"",
                "\"sameErrorAsBaselineFuzzer\": \"HappyPath\"");
        Assertions.assertThat(testHtml).contains(
                "Same error as the unmodified request",
                "href=\"Test3.html\"",
                "Test 3",
                "HappyPath",
                "problems.js",
                "id=\"problem-banner\"",
                "data-test-key=\"Test12\"");
        Assertions.assertThat(summaryJson).contains(
                "\"sameErrorAsBaselineTest\": \"Test 3\"",
                "\"sameErrorAsBaselineFuzzer\": \"HappyPath\"");
        Assertions.assertThat(problemsJs)
                .startsWith("window.CATS_PROBLEMS_PAGE = true;")
                .contains("window.CATS_PROBLEMS = ", "\"Test12\":", "\"title\": \"Server error (HTTP 500): Database down\"", "\"count\": 2",
                        "\"anchor\": \"problem-", "\"kind\": \"SERVER_ERROR\"")
                .doesNotContain("\"Test1\":");
        Assertions.assertThat(summaryHtml).contains("1 of 2 also fail the same way on the unmodified request");
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
