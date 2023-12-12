package com.endava.cats.report;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.KeyValuePair;
import com.endava.cats.model.TimeExecution;
import com.endava.cats.model.TimeExecutionDetails;
import com.endava.cats.model.ann.ExcludeTestCaseStrategy;
import com.endava.cats.json.KeyValueSerializer;
import com.endava.cats.json.LongTypeSerializer;
import com.endava.cats.util.ConsoleUtils;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fusesource.jansi.Ansi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    private static final String REPORT_JS = "cats-summary-report.json";

    private static final String EXECUTION_TIME_REPORT = "execution_times.json";
    private static final String HTML = ".html";
    private static final String JSON = ".json";
    private static final Mustache TEST_CASE_MUSTACHE = mustacheFactory.compile("test-case.mustache");
    public static final String STACKTRACE = "Stacktrace";

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(TestCaseExporter.class);

    ReportingArguments reportingArguments;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String version;

    private Path reportingPath;
    private long t0;
    private final Gson maskingSerializer;

    @Inject
    public TestCaseExporter(ReportingArguments reportingArguments) {
        this.reportingArguments = reportingArguments;
        maskingSerializer = new GsonBuilder()
                .setLenient()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .setExclusionStrategies(new ExcludeTestCaseStrategy())
                .registerTypeAdapter(Long.class, new LongTypeSerializer())
                .registerTypeAdapter(KeyValuePair.class, new KeyValueSerializer(reportingArguments.getMaskedHeaders()))
                .serializeNulls()
                .create();
    }

    public void initPath(String folder) throws IOException {
        String outputFolder = reportingArguments.getOutputReportFolder();
        if (!StringUtils.isBlank(folder)) {
            outputFolder = folder;
        }
        String subFolder = reportingArguments.isTimestampReports() ? String.valueOf(System.currentTimeMillis()) : "";
        reportingPath = Paths.get(outputFolder, subFolder);

        if (!reportingArguments.isTimestampReports() && reportingPath.toFile().exists()) {
            deleteFiles(reportingPath);
        }
        if (!reportingPath.toFile().exists()) {
            Files.createDirectories(reportingPath);
        }

        t0 = System.currentTimeMillis();
    }

    private void deleteFiles(Path path) throws IOException {
        logger.debug("Start cleaning up cats-report folder ...");
        File[] files = path.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()) {
                    Files.delete(file.toPath());
                }
            }
        }
        logger.debug("Cleanup complete!");
    }

    public void writePerformanceReport(Map<String, CatsTestCase> testCaseMap) {
        if (reportingArguments.isPrintExecutionStatistics()) {
            Map<String, List<CatsTestCase>> executionDetails = extractExecutionDetails(testCaseMap);

            ConsoleUtils.renderHeader(" Execution time details ");
            ConsoleUtils.emptyLine();
            executionDetails.forEach(this::writeExecutionTimesForPathAndHttpMethod);
        } else {
            ConsoleUtils.emptyLine();
            logger.info("Skip printing time execution statistics. You can use --printExecutionStatistics to enable this feature!");
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
        logger.timer(ansi().fgYellow().a("Average response time: {}ms").reset().toString(), ansi().bold().a(NumberFormat.getInstance().format(timeExecutionDetails.getAverage())));
        logger.timer(ansi().fgRed().a("Worst case response time: {}").reset().toString(), ansi().bold().a(timeExecutionDetails.getWorstCase().executionTimeString()));
        logger.timer(ansi().fgGreen().a("Best case response time: {}").reset().toString(), ansi().bold().a(timeExecutionDetails.getBestCase().executionTimeString()));

        if (reportingArguments.isPrintDetailedExecutionStatistics()) {
            logger.timer("{} executed tests (sorted by response time):  {}", timeExecutionDetails.getExecutions().size(), timeExecutionDetails.getExecutions());
            logger.noFormat(" ");
        }
        try {
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), EXECUTION_TIME_REPORT), maskingSerializer.toJson(timeExecutionDetails));
        } catch (IOException e) {
            logger.warning("There was an issue writing the execution_times.js: {}. Please check if CATS has proper right to write in the report location: {}",
                    e.getMessage(), reportingPath.toFile().getAbsolutePath());
            logger.debug(STACKTRACE, e);
        }
    }

    public void printExecutionDetails(ExecutionStatisticsListener executionStatisticsListener) {
        String catsFinished = ansi().fgBlue().a("CATS finished in {}. Total requests {}. ").toString();
        String passed = ansi().fgGreen().bold().a("✔ Passed {}, ").toString();
        String warnings = ansi().fgYellow().bold().a("⚠ warnings: {}, ").toString();
        String errors = ansi().fgRed().bold().a("‼ errors: {}, ").toString();
        String check = ansi().reset().fgBlue().a(String.format("You can open the report here: %s ", reportingPath.toUri() + REPORT_HTML)).reset().toString();
        String finalMessage = catsFinished + passed + warnings + errors + check;
        String duration = Duration.ofMillis(System.currentTimeMillis() - t0).toString().toLowerCase(Locale.ROOT).substring(2);

        ConsoleUtils.emptyLine();
        logger.star(finalMessage, duration, executionStatisticsListener.getAll(), executionStatisticsListener.getSuccess(), executionStatisticsListener.getWarns(), executionStatisticsListener.getErrors(), executionStatisticsListener.getSkipped());
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
        context.put("EXECUTION", Duration.ofSeconds(report.getExecutionTime()).toString().toLowerCase(Locale.ROOT).substring(2));
        context.put("VERSION", report.getCatsVersion());
        context.put("JS", this.isJavascript());
        Writer writer = this.getSummaryTemplate().execute(new StringWriter(), context);

        try {
            writer.flush();
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), this.getSummaryReportTitle()), writer.toString());
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), REPORT_JS), maskingSerializer.toJson(report));
        } catch (IOException e) {
            logger.error("There was an error writing the report summary: {}. Please check if CATS has proper right to write in the report location: {}",
                    e.getMessage(), reportingPath.toFile().getAbsolutePath());
            logger.debug(STACKTRACE, e);
        }
    }

    private CatsTestReport createTestReport(Map<String, CatsTestCase> testCaseMap, ExecutionStatisticsListener executionStatisticsListener) {
        List<CatsTestCaseSummary> summaries = testCaseMap.values().stream()
                .filter(CatsTestCase::isNotSkipped)
                .map(CatsTestCaseSummary::fromCatsTestCase)
                .sorted()
                .toList();

        return CatsTestReport.builder().testCases(summaries).errors(executionStatisticsListener.getErrors())
                .success(executionStatisticsListener.getSuccess()).totalTests(executionStatisticsListener.getAll())
                .warnings(executionStatisticsListener.getWarns()).timestamp(OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.RFC_1123_DATE_TIME))
                .executionTime(((System.currentTimeMillis() - t0) / 1000))
                .catsVersion(this.version).build();
    }

    public void writeHelperFiles() {
        try {
            writeAssets();
            for (String file : this.getSpecificHelperFiles()) {
                try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(file)) {
                    Files.copy(Objects.requireNonNull(stream), Paths.get(reportingPath.toFile().getAbsolutePath(), file));
                }
            }
        } catch (IOException e) {
            logger.error("Unable to write reporting files: {}. Please check if CATS has proper right to write in the report location: {}",
                    e.getMessage(), reportingPath.toFile().getAbsolutePath());
            logger.debug(STACKTRACE, e);
        }
    }

    public void writeAssets() throws IOException {
        Path assetsPath = Paths.get(reportingPath.toFile().getAbsolutePath(), "assets");
        Files.createDirectories(assetsPath);

        try (ZipInputStream zis = new ZipInputStream(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("assets.zip")))) {
            ZipEntry zipEntry = zis.getNextEntry();

            while (zipEntry != null) {
                Files.copy(zis, Paths.get(assetsPath.toFile().getAbsolutePath(), zipEntry.getName()), StandardCopyOption.REPLACE_EXISTING);
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
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
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), testFileName), maskingSerializer.toJson(testCase));
        } catch (IOException e) {
            logger.error("There was a problem writing test case {}: {}. Please check if CATS has proper right to write in the report location: {}",
                    testCase.getTestId(), e.getMessage(), reportingPath.toFile().getAbsolutePath());
            logger.debug(STACKTRACE, e);
        }
    }

    private void writeHtmlTestCase(CatsTestCase testCase) {
        StringWriter stringWriter = new StringWriter();
        Map<String, Object> context = new HashMap<>();
        testCase.setJs(this.isJavascript());
        testCase.setMaskingSerializer(maskingSerializer);
        context.put("TEST_CASE", testCase);
        context.put("TIMESTAMP", OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.RFC_1123_DATE_TIME));
        context.put("VERSION", this.version);
        context.put("JS", this.isJavascript());
        Writer writer = TEST_CASE_MUSTACHE.execute(stringWriter, context);
        String testFileName = testCase.getTestId().replace(" ", "").concat(HTML);
        try {
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), testFileName), writer.toString());
        } catch (IOException e) {
            logger.error("There was a problem writing test case {}: {}. Please check if CATS has proper right to write in the report location: {}",
                    testCase.getTestId(), e.getMessage(), reportingPath.toFile().getAbsolutePath());
            logger.debug(STACKTRACE, e);
        }
    }

    protected boolean isJavascript() {
        return false;
    }

    public abstract String[] getSpecificHelperFiles();

    public abstract ReportingArguments.ReportFormat reportFormat();

    public abstract Mustache getSummaryTemplate();

    public abstract String getSummaryReportTitle();
}
