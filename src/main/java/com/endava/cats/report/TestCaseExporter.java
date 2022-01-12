package com.endava.cats.report;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.ReportingArguments;
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

    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(TestCaseExporter.class);
    private static final String REPORT_HTML = "index.html";
    private static final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
    private static final String HTML = ".html";
    private static final String JSON = ".json";
    private static final String TEST_CASES_FOLDER = "cats-report";
    private static final Mustache TEST_CASE_MUSTACHE = mustacheFactory.compile("test-case.mustache");
    private static final Mustache SUMMARY_MUSTACHE = mustacheFactory.compile("summary.mustache");

    @Inject
    ReportingArguments reportingArguments;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String version;

    private Path path;
    private long t0;

    public void initPath() throws IOException {
        String subFolder = reportingArguments.isTimestampReports() ? String.valueOf(System.currentTimeMillis()) : "";
        path = Paths.get(TEST_CASES_FOLDER, subFolder);

        if (!reportingArguments.isTimestampReports() && path.toFile().exists()) {
            deleteFiles(path);
        }
        if (!path.toFile().exists()) {
            Files.createDirectories(path);
        }

        t0 = System.currentTimeMillis();
    }

    private void deleteFiles(Path path) throws IOException {
        LOGGER.start("Start cleaning up cats-report folder ...");
        File[] files = path.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()) {
                    Files.delete(file.toPath());
                }
            }
        }
        LOGGER.complete("Cleanup complete!");
    }

    public void writePerformanceReport(Map<String, CatsTestCase> testCaseMap) {
        if (reportingArguments.isPrintExecutionStatistics()) {
            Map<String, List<CatsTestCase>> executionDetails = extractExecutionDetails(testCaseMap);

            LOGGER.info(" ");
            LOGGER.info(" ---------------------------- Execution time details ---------------------------- ");
            LOGGER.info(" ");
            executionDetails.forEach(this::writeExecutionTimesForPathAndHttpMethod);
            LOGGER.info(" ");
        } else {
            LOGGER.skip("Skip printing time execution statistics. You can use --printExecutionStatistics to enable this feature!");
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
        List<CatsTestCase> sortedRuns = value.stream().sorted(Comparator.comparingLong(testCase -> testCase.getResponse().getResponseTimeInMs())).collect(Collectors.toList());
        CatsTestCase bestCase = sortedRuns.get(0);
        CatsTestCase worstCase = sortedRuns.get(sortedRuns.size() - 1);
        List<String> executions = sortedRuns.stream().map(CatsTestCase::executionTimeString).collect(Collectors.toList());
        TimeExecutionDetails timeExecutionDetails = TimeExecutionDetails.builder().average(average).
                path(key).bestCase(bestCase.executionTimeString()).worstCase(worstCase.executionTimeString()).
                executions(executions).build();


        LOGGER.info("Details for path {} ", ansi().fg(Ansi.Color.GREEN).a(timeExecutionDetails.getPath()).reset());
        LOGGER.note(ansi().fgYellow().a("Average response time: {}ms").reset().toString(), ansi().bold().a(NumberFormat.getInstance().format(timeExecutionDetails.getAverage())));
        LOGGER.note(ansi().fgRed().a("Worst case response time: {}").reset().toString(), ansi().bold().a(timeExecutionDetails.getWorstCase()));
        LOGGER.note(ansi().fgGreen().a("Best case response time: {}").reset().toString(), ansi().bold().a(timeExecutionDetails.getBestCase()));

        if (reportingArguments.isPrintDetailedExecutionStatistics()) {
            LOGGER.note("{} executed tests (sorted by response time):  {}", timeExecutionDetails.getExecutions().size(), timeExecutionDetails.getExecutions());
            LOGGER.info(" ");
        }
    }

    public void printExecutionDetails(ExecutionStatisticsListener executionStatisticsListener) {
        String catsFinished = ansi().fgBlue().a("CATS finished in {} ms. Total (excluding skipped) requests {}. ").toString();
        String passed = ansi().fgGreen().bold().a("✔ Passed {}, ").toString();
        String warnings = ansi().fgYellow().bold().a("⚠ warnings: {}, ").toString();
        String errors = ansi().fgRed().bold().a("‼ errors: {}, ").toString();
        String skipped = ansi().fgCyan().bold().a("❯ skipped: {}. ").toString();
        String check = ansi().reset().fgBlue().a(String.format("You can open the report here: %s ", path.toUri() + REPORT_HTML)).reset().toString();
        String finalMessage = catsFinished + passed + warnings + errors + skipped + check;

        LOGGER.complete(finalMessage, (System.currentTimeMillis() - t0), executionStatisticsListener.getAll(), executionStatisticsListener.getSuccess(), executionStatisticsListener.getWarns(), executionStatisticsListener.getErrors(), executionStatisticsListener.getSkipped());
    }


    public void writeSummary(Map<String, CatsTestCase> testCaseMap, ExecutionStatisticsListener executionStatisticsListener) {
        CatsTestReport report = this.createTestReport(testCaseMap, executionStatisticsListener);

        Map<String, Object> context = new HashMap<>();
        context.put("WARNINGS", report.getWarnings());
        context.put("SUCCESS", report.getSuccess());
        context.put("ERRORS", report.getErrors());
        context.put("TOTAL", report.getTotalTests());
        context.put("TIMESTAMP", report.getTimestamp());
        context.put("TEST_CASES", report.getSummaryList());
        context.put("EXECUTION", report.getExecutionTime());
        context.put("VERSION", report.getCatsVersion());
        context.putAll(this.getSpecificContext(report));
        Writer writer = SUMMARY_MUSTACHE.execute(new StringWriter(), context);

        try {
            writer.flush();
            Files.writeString(Paths.get(path.toFile().getAbsolutePath(), REPORT_HTML), writer.toString());
        } catch (IOException e) {
            LOGGER.error("There was an error writing the report summary: {}", e.getMessage(), e);
        }
    }

    private CatsTestReport createTestReport(Map<String, CatsTestCase> testCaseMap, ExecutionStatisticsListener executionStatisticsListener) {
        List<CatsTestCaseSummary> summaries = testCaseMap.entrySet().stream()
                .filter(entry -> entry.getValue().isNotSkipped())
                .map(testCase -> CatsTestCaseSummary.fromCatsTestCase(testCase.getKey(), testCase.getValue())).sorted()
                .collect(Collectors.toList());

        return CatsTestReport.builder().summaryList(summaries).errors(executionStatisticsListener.getErrors())
                .success(executionStatisticsListener.getSuccess()).totalTests(executionStatisticsListener.getAll())
                .warnings(executionStatisticsListener.getWarns()).timestamp(OffsetDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
                .executionTime(((System.currentTimeMillis() - t0) / 1000))
                .catsVersion(this.version).build();
    }

    public void writeHelperFiles() {
        for (String file : this.getSpecificHelperFiles()) {
            try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(file)) {
                Files.copy(stream, Paths.get(path.toFile().getAbsolutePath(), file));
            } catch (IOException e) {
                LOGGER.error("Unable to write reporting files!", e);
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
            Files.writeString(Paths.get(path.toFile().getAbsolutePath(), testFileName), JsonUtils.GSON.toJson(testCase));
        } catch (IOException e) {
            LOGGER.error("There was a problem writing test case {}: {}", testCase.getTestId(), e.getMessage(), e);
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
            Files.writeString(Paths.get(path.toFile().getAbsolutePath(), testFileName), writer.toString());
        } catch (IOException e) {
            LOGGER.error("There was a problem writing test case {}: {}", testCase.getTestId(), e.getMessage(), e);
        }
    }


    public abstract String[] getSpecificHelperFiles();

    public abstract Map<String, Object> getSpecificContext(CatsTestReport report);

    public abstract ReportingArguments.ReportFormat reportFormat();

}
