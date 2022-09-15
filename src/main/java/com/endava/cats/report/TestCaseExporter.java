package com.endava.cats.report;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.TimeExecution;
import com.endava.cats.model.TimeExecutionDetails;
import com.endava.cats.model.report.CatsTestCase;
import com.endava.cats.model.report.CatsTestCaseSummary;
import com.endava.cats.model.report.CatsTestReport;
import com.endava.cats.model.util.JsonUtils;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fusesource.jansi.Ansi;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * This class is responsible for writing the final report file(s).
 */

public abstract class TestCaseExporter {

    protected static final String REPORT_HTML = "index.html";
    protected static final String JUNIT_XML = "junit.xml";
    protected static final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
    protected static final Mustache SUMMARY_MUSTACHE = mustacheFactory.compile("summary.mustache");
    protected static final Mustache JUNIT_SUMMARY_MUSTACHE = mustacheFactory.compile("junit_summary.mustache");
    private static final String REPORT_JS = "cats-summary-report.js";

    private static final String EXECUTION_TIME_REPORT = "execution_times.js";
    private static final String HTML = ".html";
    private static final String JSON = ".json";
    private static final Mustache TEST_CASE_MUSTACHE = mustacheFactory.compile("test-case.mustache");

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(TestCaseExporter.class);

    @Inject
    ReportingArguments reportingArguments;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String version;

    private Path reportingPath;
    private long t0;

    public void initPath() throws IOException {
        String subFolder = reportingArguments.isTimestampReports() ? String.valueOf(System.currentTimeMillis()) : "";
        reportingPath = Paths.get(reportingArguments.getOutputReportFolder(), subFolder);

        if (!reportingArguments.isTimestampReports() && reportingPath.toFile().exists()) {
            deleteFiles(reportingPath);
        }
        if (!reportingPath.toFile().exists()) {
            Files.createDirectories(reportingPath);
        }

        t0 = System.currentTimeMillis();
    }

    private void deleteFiles(Path path) throws IOException {
        logger.start("Start cleaning up cats-report folder ...");
        File[] files = path.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()) {
                    Files.delete(file.toPath());
                }
            }
        }
        logger.complete("Cleanup complete!");
    }

    public void writePerformanceReport(Map<String, CatsTestCase> testCaseMap) {
        if (reportingArguments.isPrintExecutionStatistics()) {
            Map<String, List<CatsTestCase>> executionDetails = extractExecutionDetails(testCaseMap);

            logger.noFormat(" ");
            logger.info("---------------------------- Execution time details ----------------------------");
            logger.noFormat(" ");
            executionDetails.forEach(this::writeExecutionTimesForPathAndHttpMethod);
            logger.noFormat(" ");
        } else {
            logger.skip("Skip printing time execution statistics. You can use --printExecutionStatistics to enable this feature!");
        }
    }

    private Map<String, List<CatsTestCase>> extractExecutionDetails(Map<String, CatsTestCase> testCaseMap) {
        Map<String, CatsTestCase> allRun = testCaseMap.entrySet().stream().filter(entry -> entry.getValue().isNotSkipped() && entry.getValue().notIgnoredForExecutionStatistics())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return allRun.values().stream()
                .collect(Collectors.groupingBy(testCase -> testCase.getResponse().getHttpMethod() + " " + testCase.getPath()))
                .entrySet().stream().filter(entry -> entry.getValue().size() > 1).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void writeExecutionTimesForPathAndHttpMethod(String key, List<CatsTestCase> value) {
        double average = value.stream().mapToLong(testCase -> testCase.getResponse().getResponseTimeInMs()).average().orElse(0);
        List<CatsTestCase> sortedRuns = value.stream().sorted(Comparator.comparingLong(testCase -> testCase.getResponse().getResponseTimeInMs())).toList();
        CatsTestCase bestCaseTestCase = sortedRuns.get(0);
        CatsTestCase worstCaseTestCase = sortedRuns.get(sortedRuns.size() - 1);
        List<TimeExecution> executions = sortedRuns.stream()
                .map(tetCase -> TimeExecution.builder()
                        .testId(tetCase.getTestId())
                        .executionInMs(tetCase.getResponse().getResponseTimeInMs())
                        .build())
                .toList();

        TimeExecutionDetails timeExecutionDetails = TimeExecutionDetails.builder().average(average)
                .path(key).bestCase(TimeExecution.builder()
                        .testId(bestCaseTestCase.getTestId())
                        .executionInMs(bestCaseTestCase.getResponse().getResponseTimeInMs())
                        .build())
                .worstCase(TimeExecution.builder()
                        .testId(worstCaseTestCase.getTestId())
                        .executionInMs(worstCaseTestCase.getResponse().getResponseTimeInMs())
                        .build())
                .executions(executions).build();


        logger.info("Details for path {} ", ansi().fg(Ansi.Color.GREEN).a(timeExecutionDetails.getPath()).reset());
        logger.note(ansi().fgYellow().a("Average response time: {}ms").reset().toString(), ansi().bold().a(NumberFormat.getInstance().format(timeExecutionDetails.getAverage())));
        logger.note(ansi().fgRed().a("Worst case response time: {}").reset().toString(), ansi().bold().a(timeExecutionDetails.getWorstCase().executionTimeString()));
        logger.note(ansi().fgGreen().a("Best case response time: {}").reset().toString(), ansi().bold().a(timeExecutionDetails.getBestCase().executionTimeString()));

        if (reportingArguments.isPrintDetailedExecutionStatistics()) {
            logger.note("{} executed tests (sorted by response time):  {}", timeExecutionDetails.getExecutions().size(), timeExecutionDetails.getExecutions());
            logger.noFormat(" ");
        }
        try {
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), EXECUTION_TIME_REPORT), JsonUtils.GSON.toJson(timeExecutionDetails));
        } catch (IOException e) {
            logger.warning("There was an issue writing the execution_times.js: {}", e.getMessage());
            logger.debug("Stacktrace", e);
        }
    }

    public void printExecutionDetails(ExecutionStatisticsListener executionStatisticsListener) {
        String catsFinished = ansi().fgBlue().a("CATS finished in {} ms. Total (excluding skipped) requests {}. ").toString();
        String passed = ansi().fgGreen().bold().a("✔ Passed {}, ").toString();
        String warnings = ansi().fgYellow().bold().a("⚠ warnings: {}, ").toString();
        String errors = ansi().fgRed().bold().a("‼ errors: {}, ").toString();
        String skipped = ansi().fgCyan().bold().a("❯ skipped: {}. ").toString();
        String check = ansi().reset().fgBlue().a(String.format("You can open the report here: %s ", reportingPath.toUri() + REPORT_HTML)).reset().toString();
        String finalMessage = catsFinished + passed + warnings + errors + skipped + check;

        logger.complete(finalMessage, (System.currentTimeMillis() - t0), executionStatisticsListener.getAll(), executionStatisticsListener.getSuccess(), executionStatisticsListener.getWarns(), executionStatisticsListener.getErrors(), executionStatisticsListener.getSkipped());
    }


    public void writeSummary(Map<String, CatsTestCase> testCaseMap, ExecutionStatisticsListener executionStatisticsListener) {
        CatsTestReport report = this.createTestReport(testCaseMap, executionStatisticsListener);

        Map<String, Object> context = new HashMap<>();
        context.put("WARNINGS", report.getWarnings());
        context.put("SUCCESS", report.getSuccess());
        context.put("ERRORS", report.getErrors());
        context.put("TOTAL", report.getTotalTests());
        context.put("TIMESTAMP", report.getTimestamp());
        context.put("TEST_CASES", report.getTestCases());
        context.put("EXECUTION", report.getExecutionTime());
        context.put("VERSION", report.getCatsVersion());
        context.putAll(this.getSpecificContext(report));
        Writer writer = this.getSummaryTemplate().execute(new StringWriter(), context);

        try {
            writer.flush();
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), this.getSummaryReportTitle()), writer.toString());
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), REPORT_JS), JsonUtils.GSON.toJson(report));
        } catch (IOException e) {
            logger.error("There was an error writing the report summary: {}", e.getMessage());
            logger.debug("Stacktrace", e);
        }
    }

    private CatsTestReport createTestReport(Map<String, CatsTestCase> testCaseMap, ExecutionStatisticsListener executionStatisticsListener) {
        List<CatsTestCaseSummary> summaries = testCaseMap.entrySet().stream()
                .filter(entry -> entry.getValue().isNotSkipped())
                .map(testCase -> CatsTestCaseSummary.fromCatsTestCase(testCase.getKey(), testCase.getValue())).sorted()
                .toList();

        return CatsTestReport.builder().testCases(summaries).errors(executionStatisticsListener.getErrors())
                .success(executionStatisticsListener.getSuccess()).totalTests(executionStatisticsListener.getAll())
                .warnings(executionStatisticsListener.getWarns()).timestamp(OffsetDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
                .executionTime(((System.currentTimeMillis() - t0) / 1000))
                .catsVersion(this.version).build();
    }

    public void writeHelperFiles() {
        for (String file : this.getSpecificHelperFiles()) {
            try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(file)) {
                Files.copy(stream, Paths.get(reportingPath.toFile().getAbsolutePath(), file));
            } catch (IOException e) {
                logger.error("Unable to write reporting files: {}", e.getMessage());
                logger.debug("Stacktrace", e);
            }
        }
    }

    /**
     * We mark it as DryRun in order to avoid writing test cases when in dryRun mode.
     *
     * @param testCase the current test case
     */
    @DryRun
    public void writeTestCase(CatsTestCase testCase) {
        writeHtmlTestCase(testCase);
        writeJsonTestCase(testCase);
    }

    private void writeJsonTestCase(CatsTestCase testCase) {
        String testFileName = testCase.getTestId().replace(" ", "").concat(JSON);
        try {
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), testFileName), JsonUtils.GSON.toJson(testCase));
        } catch (IOException e) {
            logger.error("There was a problem writing test case {}: {}", testCase.getTestId(), e.getMessage());
            logger.debug("Stacktrace", e);
        }
    }

    private void writeHtmlTestCase(CatsTestCase testCase) {
        StringWriter stringWriter = new StringWriter();
        Map<String, Object> context = new HashMap<>();
        context.put("TEST_CASE", testCase);
        context.put("TIMESTAMP", OffsetDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
        context.put("VERSION", this.version);
        Writer writer = TEST_CASE_MUSTACHE.execute(stringWriter, context);
        String testFileName = testCase.getTestId().replace(" ", "").concat(HTML);
        try {
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), testFileName), writer.toString());
        } catch (IOException e) {
            logger.error("There was a problem writing test case {}: {}", testCase.getTestId(), e.getMessage());
            logger.debug("Stacktrace", e);
        }
    }


    public abstract String[] getSpecificHelperFiles();

    public abstract Map<String, Object> getSpecificContext(CatsTestReport report);

    public abstract ReportingArguments.ReportFormat reportFormat();

    public abstract Mustache getSummaryTemplate();

    public abstract String getSummaryReportTitle();
}
