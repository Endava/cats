package com.endava.cats.report;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsConfiguration;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseExecutionSummary;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.TimeExecution;
import com.endava.cats.model.TimeExecutionDetails;
import com.endava.cats.model.ann.ExcludeTestCaseStrategy;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.KeyValueSerializer;
import com.endava.cats.util.LongTypeSerializer;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fusesource.jansi.Ansi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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
    static final String REPORT_HTML = "index.html";
    static final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
    static final Mustache SUMMARY_MUSTACHE = mustacheFactory.compile("summary.mustache");
    private static final String REPORT_JS = "cats-summary-report.json";
    private static final String EXECUTION_TIME_REPORT = "execution_times.json";
    private static final String HTML = ".html";
    private static final String JSON = ".json";
    private static final Mustache TEST_CASE_MUSTACHE = mustacheFactory.compile("test-case.mustache");
    private static final String STACKTRACE = "Stacktrace";
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(TestCaseExporter.class);

    ReportingArguments reportingArguments;
    CatsConfiguration catsConfiguration;

    private Path reportingPath;
    private long t0;
    private final Gson maskingSerializer;

    @Getter
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String appVersion;

    String osDetails;


    /**
     * Constructs a new instance of TestCaseExporter with the specified reporting arguments.
     *
     * @param reportingArguments the reporting arguments for configuring the TestCaseExporter
     */
    @Inject
    protected TestCaseExporter(ReportingArguments reportingArguments, CatsGlobalContext catsGlobalContext) {
        this.reportingArguments = reportingArguments;
        this.catsConfiguration = catsGlobalContext.getCatsConfiguration();
        maskingSerializer = new GsonBuilder()
                .setLenient()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .setExclusionStrategies(new ExcludeTestCaseStrategy())
                .registerTypeAdapter(Long.class, new LongTypeSerializer())
                .registerTypeAdapter(KeyValuePair.class, new KeyValueSerializer(reportingArguments.getMaskedHeaders()))
                .serializeNulls()
                .create();
        this.osDetails = System.getProperty("os.name") + "-" + System.getProperty("os.version") + "-" + System.getProperty("os.arch");

    }

    /**
     * Initializes the reporting path for the test reports.
     *
     * @param folder The custom output folder path. If not provided (or blank), the default folder from reporting arguments is used.
     * @throws IOException If an I/O error occurs during file or directory operations.
     */
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

    /**
     * Writes performance statistics for the executed test cases, including execution time details.
     * The method checks if printing execution statistics is enabled in the reporting arguments before generating and printing the report.
     *
     * @param executionSummaries a map containing the summaries of executed test cases
     */
    public void writePerformanceReport(List<CatsTestCaseExecutionSummary> executionSummaries) {
        if (reportingArguments.isPrintExecutionStatistics()) {
            Map<String, List<CatsTestCaseExecutionSummary>> executionDetails = extractExecutionDetails(executionSummaries);

            ConsoleUtils.renderHeader(" Execution time details ");
            ConsoleUtils.emptyLine();
            executionDetails.forEach(this::writeExecutionTimesForPathAndHttpMethod);
        } else {
            ConsoleUtils.emptyLine();
            logger.info("Skip printing time execution statistics. You can use --printExecutionStatistics to enable this feature!");
        }
    }

    private Map<String, List<CatsTestCaseExecutionSummary>> extractExecutionDetails(List<CatsTestCaseExecutionSummary> summaries) {
        return summaries
                .stream()
                .collect(Collectors.groupingBy(testCase -> testCase.httpMethod() + " " + testCase.path()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void writeExecutionTimesForPathAndHttpMethod(String key, List<CatsTestCaseExecutionSummary> value) {
        double average = value.stream().mapToLong(CatsTestCaseExecutionSummary::responseTimeInMs).average().orElse(0);
        List<CatsTestCaseExecutionSummary> sortedRuns = value.stream()
                .sorted(Comparator.comparingLong(CatsTestCaseExecutionSummary::responseTimeInMs))
                .toList();

        CatsTestCaseExecutionSummary bestCaseTestCase = sortedRuns.get(0);
        CatsTestCaseExecutionSummary worstCaseTestCase = sortedRuns.get(sortedRuns.size() - 1);
        List<TimeExecution> executions = sortedRuns.stream()
                .map(tetCase -> TimeExecution.builder()
                        .testId(tetCase.testId())
                        .executionInMs(tetCase.responseTimeInMs())
                        .build())
                .toList();

        TimeExecutionDetails timeExecutionDetails = TimeExecutionDetails.builder().average(average)
                .path(key).bestCase(TimeExecution.builder()
                        .testId(bestCaseTestCase.testId())
                        .executionInMs(bestCaseTestCase.responseTimeInMs())
                        .build())
                .worstCase(TimeExecution.builder()
                        .testId(worstCaseTestCase.testId())
                        .executionInMs(worstCaseTestCase.responseTimeInMs())
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
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), EXECUTION_TIME_REPORT), maskingSerializer.toJson(timeExecutionDetails), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warning("There was an issue writing the execution_times.js: {}. Please check if CATS has proper right to write in the report location: {}",
                    e.getMessage(), reportingPath.toFile().getAbsolutePath());
            logger.debug(STACKTRACE, e);
        }
    }

    /**
     * Prints the execution details including the overall CATS execution time, the total number of requests, and statistics on passed, warnings, and errors.
     * It also provides a message with a link to the generated report if available.
     *
     * @param executionStatisticsListener the listener providing statistics on CATS execution
     */
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


    /**
     * Writes a summary report based on the provided test case map and execution statistics.
     * It creates a CatsTestReport and extracts information such as warnings, success, errors, and total tests.
     * The gathered information is stored in a context map.
     *
     * @param summaries                   the pre-created summary for each test case
     * @param executionStatisticsListener the listener providing statistics on CATS execution
     */
    public void writeSummary(List<CatsTestCaseSummary> summaries, ExecutionStatisticsListener executionStatisticsListener) {
        CatsTestReport report = this.createTestReport(summaries, executionStatisticsListener);

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
        context.put("OS", this.osDetails);

        if (catsConfiguration != null) {
            context.put("CONTRACT_NAME", catsConfiguration.contract());
            context.put("BASE_URL", catsConfiguration.basePath());
            context.put("HTTP_METHODS", catsConfiguration.httpMethods().toString());
            context.put("FUZZERS", catsConfiguration.fuzzers());
            context.put("PATHS", catsConfiguration.paths());
        }

        Writer writer = this.getSummaryTemplate().execute(new StringWriter(), context);

        try {
            writer.flush();
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), this.getSummaryReportTitle()), writer.toString(), StandardCharsets.UTF_8);
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), REPORT_JS), maskingSerializer.toJson(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("There was an error writing the report summary: {}. Please check if CATS has proper right to write in the report location: {}",
                    e.getMessage(), reportingPath.toFile().getAbsolutePath());
            logger.debug(STACKTRACE, e);
        }
    }

    private CatsTestReport createTestReport(List<CatsTestCaseSummary> summaries, ExecutionStatisticsListener executionStatisticsListener) {
        List<CatsTestCaseSummary> sortedSummaries = summaries.stream().sorted().toList();

        return CatsTestReport.builder().testCases(sortedSummaries).errors(executionStatisticsListener.getErrors())
                .success(executionStatisticsListener.getSuccess()).totalTests(executionStatisticsListener.getAll())
                .warnings(executionStatisticsListener.getWarns()).timestamp(OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.RFC_1123_DATE_TIME))
                .executionTime(((System.currentTimeMillis() - t0) / 1000))
                .catsVersion(appVersion).build();
    }

    /**
     * Writes helper files, such as assets and specific files, to the reporting path.
     * It includes assets and copies specific helper files from the classpath to the reporting path.
     * The specific helper files are determined by the implementation of getSpecificHelperFiles method.
     */
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

    /**
     * Writes assets to the reporting path. Assets are stored in a directory named "assets"
     * within the reporting path. It creates the "assets" directory if it doesn't exist.
     *
     * @throws IOException if an I/O error occurs while creating directories
     */
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
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), testFileName), maskingSerializer.toJson(testCase), StandardCharsets.UTF_8);
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
        context.put("VERSION", appVersion);
        context.put("JS", this.isJavascript());
        Writer writer = TEST_CASE_MUSTACHE.execute(stringWriter, context);
        String testFileName = testCase.getTestId().replace(" ", "").concat(HTML);
        try {
            Files.writeString(Paths.get(reportingPath.toFile().getAbsolutePath(), testFileName), writer.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("There was a problem writing test case {}: {}. Please check if CATS has proper right to write in the report location: {}",
                    testCase.getTestId(), e.getMessage(), reportingPath.toFile().getAbsolutePath());
            logger.debug(STACKTRACE, e);
        } catch (NegativeArraySizeException e) {
            logger.debug(e.getMessage());
            logger.debug(STACKTRACE, e);
        }
    }

    /**
     * Indicates whether the report format involves JavaScript functionality.
     *
     * @return {@code true} if the report format requires JavaScript, {@code false} otherwise.
     */
    protected boolean isJavascript() {
        return false;
    }

    /**
     * Retrieves an array of specific helper files required for the reporting format.
     *
     * @return An array of file names representing the specific helper files.
     */
    public abstract String[] getSpecificHelperFiles();

    /**
     * Retrieves the report format associated with the exporter.
     *
     * @return The report format of the exporter.
     */
    public abstract ReportingArguments.ReportFormat reportFormat();

    /**
     * Retrieves the Mustache template used for generating the summary section of the report.
     *
     * @return The Mustache template for the summary section.
     */
    public abstract Mustache getSummaryTemplate();

    /**
     * Retrieves the title for the summary report.
     *
     * @return The title for the summary report.
     */
    public abstract String getSummaryReportTitle();
}
