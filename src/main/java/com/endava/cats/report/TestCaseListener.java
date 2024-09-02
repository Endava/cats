package com.endava.cats.report;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseExecutionSummary;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import com.google.common.collect.Iterators;
import com.google.common.net.MediaType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.Builder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fusesource.jansi.Ansi;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.endava.cats.context.CatsGlobalContext.CONTRACT_PATH;
import static com.endava.cats.context.CatsGlobalContext.HTTP_METHOD;
import static com.endava.cats.model.CatsTestCase.SKIP_REPORTING;
import static org.fusesource.jansi.Ansi.ansi;

/**
 * This class exposes methods to record the progress of a test case
 */
@ApplicationScoped
@DryRun
public class TestCaseListener {
    private static final Iterator<Character> cycle = Iterators.cycle('\\', '\\', '\\', '|', '|', '|', '/', '/', '/', '-', '-', '-');
    private static final String DEFAULT = "*******";
    static final String ID = "id";
    private static final String FUZZER_KEY = "fuzzerKey";
    private static final String FUZZER = "fuzzer";
    private static final String ID_ANSI = "id_ansi";
    static final AtomicInteger TEST = new AtomicInteger(0);
    private static final List<String> NOT_NECESSARILY_DOCUMENTED = Arrays.asList("406", "415", "414", "501", "413", "431");
    private static final String RECEIVED_RESPONSE_IS_MARKED_AS_IGNORED_SKIPPING = "Received response is marked as ignored... skipping!";
    private static final List<String> CONTENT_TYPE_DONT_MATCH_SCHEMA = List.of("application/csv", "application/pdf");
    final Map<String, CatsTestCase> testCaseMap = new HashMap<>();
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(TestCaseListener.class);
    private static final String SEPARATOR = "-".repeat(ConsoleUtils.getConsoleColumns(22));
    private final ExecutionStatisticsListener executionStatisticsListener;
    private final TestCaseExporter testCaseExporter;
    private final CatsGlobalContext globalContext;
    private final IgnoreArguments ignoreArguments;
    private final ReportingArguments reportingArguments;
    final List<CatsTestCaseSummary> testCaseSummaryDetails = new ArrayList<>();
    final List<CatsTestCaseExecutionSummary> testCaseExecutionDetails = new ArrayList<>();

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String appVersion;
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "cats")
    String appName;
    @ConfigProperty(name = "app.timestamp", defaultValue = "1-1-1")
    String appBuildTime;

    private final Deque<String> runPerPathListener = new ArrayDeque<>();

    /**
     * Constructs a TestCaseListener with the provided dependencies and configuration.
     *
     * @param catsGlobalContext  the global context for Cats
     * @param er                 the listener for execution statistics
     * @param exporters          the available TestCaseExporter instances
     * @param filterArguments    the arguments for filtering test cases
     * @param reportingArguments the arguments for reporting test cases
     * @throws NoSuchElementException if no matching exporter is found for the specified report format
     */
    public TestCaseListener(CatsGlobalContext catsGlobalContext, ExecutionStatisticsListener er, Instance<TestCaseExporter> exporters, IgnoreArguments filterArguments, ReportingArguments reportingArguments) {
        this.executionStatisticsListener = er;
        this.testCaseExporter = exporters.stream()
                .filter(exporter -> exporter.reportFormat() == reportingArguments.getReportFormat())
                .findFirst()
                .orElseThrow();
        this.ignoreArguments = filterArguments;
        this.globalContext = catsGlobalContext;
        this.reportingArguments = reportingArguments;
    }

    private static String replaceBrackets(String message, Object... params) {
        for (Object obj : params) {
            message = message.replaceFirst(Pattern.quote("{}"), Matcher.quoteReplacement(String.valueOf(obj)));
        }

        return message;
    }

    private String getKeyDefault() {
        return reportingArguments.isSummaryInConsole() ? "" : DEFAULT;
    }

    /**
     * Performs setup actions before fuzzing for the specified fuzzer class.
     *
     * @param fuzzer the class representing the fuzzer
     */
    public void beforeFuzz(Class<?> fuzzer, String path, String httpMethod) {
        String clazz = ConsoleUtils.removeTrimSanitize(fuzzer.getSimpleName()).replaceAll("[a-z]", "");
        MDC.put(FUZZER, ConsoleUtils.centerWithAnsiColor(clazz, getKeyDefault().length(), Ansi.Color.MAGENTA));
        MDC.put(FUZZER_KEY, ConsoleUtils.removeTrimSanitize(fuzzer.getSimpleName()));
        MDC.put(CONTRACT_PATH, path);
        MDC.put(HTTP_METHOD, httpMethod);
        this.notifySummaryObservers(path);
    }

    /**
     * Performs cleanup actions after fuzzing for a specific path and HTTP method.
     *
     * @param path the path for which fuzzing has been completed
     */
    public void afterFuzz(String path) {
        this.notifySummaryObservers(path);

        MDC.put(FUZZER, this.getKeyDefault());
        MDC.put(FUZZER_KEY, this.getKeyDefault());
        MDC.remove(CONTRACT_PATH);
        MDC.remove(HTTP_METHOD);
    }

    /**
     * Creates and executes a test by running the provided runnable.
     * Logs test start, catches exceptions during execution, logs results, and performs necessary cleanup.
     *
     * @param externalLogger the external logger for logging test-related information
     * @param fuzzer         the fuzzer associated with the test
     * @param s              the runnable representing the test logic
     */
    public void createAndExecuteTest(PrettyLogger externalLogger, Fuzzer fuzzer, Runnable s, FuzzingData data) {
        this.startTestCase(data);
        try {
            s.run();
        } catch (Exception e) {
            CatsResultFactory.CatsResult catsResult = CatsResultFactory.createUnexpectedException(fuzzer.getClass().getSimpleName(), Optional.ofNullable(e.getMessage()).orElse(""));
            this.reportResultError(externalLogger, data, catsResult.reason(), catsResult.message());
            externalLogger.error("Exception while processing: {}", e.getMessage());
            externalLogger.debug("Detailed stacktrace", e);
            this.checkForIOErrors(e);
        }
        this.endTestCase();
    }

    /**
     * Returns the current name of the fuzzer being executed.
     *
     * @return the fuzzer name that is currently being run
     */
    public String getCurrentFuzzer() {
        return MDC.get(FUZZER_KEY);
    }

    /**
     * Returns the current test case number being executed.
     *
     * @return the test case number being executed
     */
    public int getCurrentTestCaseNumber() {
        return TEST.get();
    }

    private void startTestCase(FuzzingData data) {
        String testId = String.valueOf(TEST.incrementAndGet());
        MDC.put(ID, testId);
        MDC.put(ID_ANSI, ConsoleUtils.centerWithAnsiColor(testId, 7, Ansi.Color.MAGENTA));

        CatsTestCase testCase = new CatsTestCase();
        testCase.setTestId("Test " + testId);
        testCase.setContractPath(data.getContractPath());
        testCase.setPath(data.getContractPath());
        testCase.getRequest().setHttpMethod(String.valueOf(data.getMethod()));
        testCaseMap.put(testId, testCase);
    }

    /**
     * Adds a scenario to the test case and logs it using the provided logger.
     *
     * @param logger   the logger used to log the scenario
     * @param scenario the scenario description template
     * @param params   the parameters to replace placeholders in the scenario description
     */
    public void addScenario(PrettyLogger logger, String scenario, Object... params) {
        logger.info(scenario, params);
        currentTestCase().setScenario(replaceBrackets(scenario, params));
    }

    /**
     * Adds an expected result to the test case and logs it using the provided logger.
     *
     * @param logger         the logger used to log the expected result
     * @param expectedResult the expected result description template
     * @param params         the parameters to replace placeholders in the expected result description
     */
    public void addExpectedResult(PrettyLogger logger, String expectedResult, Object... params) {
        logger.note(expectedResult, params);
        currentTestCase().setExpectedResult(replaceBrackets(expectedResult, params));
    }

    /**
     * Adds the specified path information to the current test case in the test case map.
     * The path is associated with the ongoing test case using the Mapped Diagnostic Context (MDC) identifier.
     *
     * @param path the path to be associated with the current test case
     */
    public void addPath(String path) {
        currentTestCase().setPath(path);
    }

    /**
     * Adds the specified contract path information to the current test case in the test case map.
     * The contract path is associated with the ongoing test case using the Mapped Diagnostic Context (MDC) identifier.
     *
     * @param path the contract path to be associated with the current test case
     */
    public void addContractPath(String path) {
        currentTestCase().setContractPath(path);
    }

    /**
     * Adds the specified server information to the current test case in the test case map.
     * The server information is associated with the ongoing test case using the Mapped Diagnostic Context (MDC) identifier.
     *
     * @param server the server information to be associated with the current test case
     */
    public void addServer(String server) {
        currentTestCase().setServer(server);
    }

    /**
     * Adds the specified CatsRequest to the current test case in the test case map.
     * The CatsRequest is associated with the ongoing test case using the Mapped Diagnostic Context (MDC) identifier.
     *
     * @param request the CatsRequest to be associated with the current test case
     */
    public void addRequest(CatsRequest request) {
        currentTestCase().setRequest(request);
    }

    /**
     * Adds the specified CatsResponse to the current test case in the test case map.
     * The CatsResponse is associated with the ongoing test case using the Mapped Diagnostic Context (MDC) identifier.
     *
     * @param response the CatsResponse to be associated with the current test case
     */
    public void addResponse(CatsResponse response) {
        currentTestCase().setResponse(response);
    }

    /**
     * Adds the specified full request path information to the current test case in the test case map.
     * The full request path is associated with the ongoing test case using the Mapped Diagnostic Context (MDC) identifier.
     *
     * @param fullRequestPath the full request path to be associated with the current test case
     */
    public void addFullRequestPath(String fullRequestPath) {
        currentTestCase().setFullRequestPath(fullRequestPath);
    }

    private void endTestCase() {
        CatsTestCase currentTestCase = currentTestCase();
        currentTestCase.setFuzzer(MDC.get(FUZZER_KEY));
        if (currentTestCase.isNotSkipped()) {
            testCaseExporter.writeTestCase(currentTestCase);
            keepSummary(currentTestCase);
        }
        keepExecutionDetails(currentTestCase);
        testCaseMap.remove(MDC.get(ID));
        MDC.remove(ID);
        MDC.put(ID_ANSI, this.getKeyDefault());
        logger.info(SEPARATOR);
    }

    private void keepSummary(CatsTestCase testCase) {
        testCaseSummaryDetails.add(CatsTestCaseSummary.fromCatsTestCase(testCase));
    }

    private void keepExecutionDetails(CatsTestCase testCase) {
        if (testCase.notIgnoredForExecutionStatistics() && reportingArguments.isPrintExecutionStatistics()) {
            testCaseExecutionDetails.add(new CatsTestCaseExecutionSummary(testCase.getTestId(), testCase.getPath(),
                    testCase.getHttpMethod(), testCase.getResponse().getResponseTimeInMs()));
        }
    }

    /**
     * Notifies summary observers about the progress of a specific path and HTTP method during the testing session.
     * If configured to display summaries in the console, this method renders the progress dynamically.
     *
     * @param path the path for which the progress is being reported
     */
    public void notifySummaryObservers(String path) {
        if (!reportingArguments.isSummaryInConsole()) {
            return;
        }
        String prefix = ansi().fgBlue().a("(" + runPerPathListener.size() + "/" + globalContext.getCatsConfiguration().pathsToRun() + ") ").fgDefault().toString();
        String printPath = prefix + path + ConsoleUtils.SEPARATOR + executionStatisticsListener.resultAsStringPerPath(path);

        if (runPerPathListener.contains(path)) {
            ConsoleUtils.renderSameRow(printPath, cycle.next());
        } else {
            this.markPreviousPathAsDone();
            runPerPathListener.push(path);
            ConsoleUtils.renderNewRow(printPath, cycle.next());
        }
    }

    private void markPreviousPathAsDone() {
        String previousPath = runPerPathListener.peek();
        if (previousPath != null) {
            String toRenderPreviousPath = previousPath + ConsoleUtils.SEPARATOR + executionStatisticsListener.resultAsStringPerPath(previousPath);
            ConsoleUtils.renderSameRow(toRenderPreviousPath, '✔');
        }
    }

    /**
     * Updates the progress with a new character to signal progress.
     *
     * @param data the FuzzingData context
     */
    public void updateUnknownProgress(FuzzingData data) {
        this.notifySummaryObservers(data.getContractPath());
    }

    /**
     * Starts a new testing session, initializing necessary configurations and logging session information.
     * This method sets default values for identifiers and logs session details such as application name,
     * version, build time, and platform.
     */
    public void startSession() {
        MDC.put(ID_ANSI, this.getKeyDefault());
        MDC.put(FUZZER, this.getKeyDefault());
        MDC.put(FUZZER_KEY, this.getKeyDefault());

        String osDetails = System.getProperty("os.name") + "-" + System.getProperty("os.version") + "-" + System.getProperty("os.arch");

        ConsoleUtils.emptyLine();
        logger.start(ansi().bold().a("Starting {}-{}, build time {} UTC, platform {}").reset().toString(),
                ansi().fg(Ansi.Color.GREEN).a(appName),
                ansi().fg(Ansi.Color.GREEN).a(appVersion),
                ansi().fg(Ansi.Color.GREEN).a(appBuildTime),
                ansi().fg(Ansi.Color.GREEN).a(osDetails).reset());
    }

    /**
     * Initializes the reporting path using the associated test case exporter.
     *
     * @throws IOException if an I/O error occurs during the initialization process
     */
    public void initReportingPath() throws IOException {
        testCaseExporter.initPath(null);
    }

    /**
     * Initializes the reporting path using the associated test case exporter.
     *
     * @param folder the folder path where the reporting should be initialized
     * @throws IOException if an I/O error occurs during the initialization process
     */
    public void initReportingPath(String folder) throws IOException {
        testCaseExporter.initPath(folder);
    }

    /**
     * Writes an individual test case using the associated test case exporter.
     *
     * @param catsTestCase the CatsTestCase to be written
     */
    public void writeIndividualTestCase(CatsTestCase catsTestCase) {
        testCaseExporter.writeTestCase(catsTestCase);
    }

    /**
     * Writes helper files using the associated test case exporter.
     * This method delegates the task of writing helper files to the underlying test case exporter.
     */
    public void writeHelperFiles() {
        testCaseExporter.writeHelperFiles();
    }

    /**
     * Ends the test session by performing necessary actions such as writing summaries, helper files, and performance reports.
     * Additionally, prints execution details using the associated logger.
     */
    public void endSession() {
        markPreviousPathAsDone();
        reportingArguments.enableAdditionalLoggingIfSummary();
        testCaseExporter.writeSummary(testCaseSummaryDetails, executionStatisticsListener);
        testCaseExporter.writeHelperFiles();
        testCaseExporter.writeErrorsByReason(testCaseSummaryDetails);
        testCaseExporter.writePerformanceReport(testCaseExecutionDetails);
        testCaseExporter.printExecutionDetails(executionStatisticsListener);
        writeRecordedErrorsIfPresent();
    }

    /**
     * Renders a FUZZING header if logging is SUMMARY.
     */
    public void renderFuzzingHeader() {
        if (reportingArguments.isSummaryInConsole()) {
            ConsoleUtils.renderHeader(" FUZZING ");
        }
    }

    private void writeRecordedErrorsIfPresent() {
        globalContext.writeRecordedErrorsIfPresent();
    }

    private void setResultReason(CatsResultFactory.CatsResult catsResult) {
        CatsTestCase testCase = currentTestCase();
        testCase.setResultReason(catsResult.reason());
    }

    private void setResultReason(String reason) {
        CatsTestCase testCase = currentTestCase();
        testCase.setResultReason(reason);
    }

    /**
     * If {@code --ignoreResponseCodes} is supplied and the response code received from the service
     * is in the ignored list, the method will actually report INFO instead of WARN.
     * If {@code --skipReportingForIgnoredCodes} is also enabled, the reporting for these ignored codes will be skipped entirely.
     *
     * @param logger  the current logger
     * @param message message to be logged
     * @param params  params needed by the message
     */
    void reportWarn(PrettyLogger logger, String message, Object... params) {
        this.logger.debug("Reporting warn with message: {}", replaceBrackets(message, params));
        CatsTestCase testCase = currentTestCase();
        CatsResponse catsResponse = Optional.ofNullable(testCase.getResponse()).orElse(CatsResponse.empty());

        if (ignoreArguments.isSkipReportingForWarnings()) {
            this.logger.debug(RECEIVED_RESPONSE_IS_MARKED_AS_IGNORED_SKIPPING);
            this.skipTest(logger, replaceBrackets("Skip reporting as --skipReportingForWarnings is enabled"));
            this.recordResult(message, params, SKIP_REPORTING, logger);
        } else if (ignoreArguments.isNotIgnoredResponse(catsResponse)) {
            this.logger.debug("Received response is not marked as ignored... reporting warn!");
            executionStatisticsListener.increaseWarns(testCase.getContractPath());
            logger.warning(message, params);
            this.recordResult(message, params, Level.WARN.toString().toLowerCase(), logger);
        } else if (ignoreArguments.isSkipReportingForIgnoredCodes()) {
            this.logger.debug(RECEIVED_RESPONSE_IS_MARKED_AS_IGNORED_SKIPPING);
            this.skipTest(logger, replaceBrackets("Some response elements were marked as ignored and --skipReportingForIgnoredCodes is enabled."));
            this.recordResult(message, params, SKIP_REPORTING, logger);
        } else {
            testCase.setResultIgnoreDetails(Level.WARN.toString());
            this.logger.debug("Received response is marked as ignored... reporting info!");
            this.reportInfo(logger, message, params);
        }
    }

    private void reportWarnOrInfoBasedOnCheck(PrettyLogger logger, FuzzingData data, CatsResultFactory.CatsResult catsResult, boolean ignoreCheck, Object... params) {
        if (ignoreCheck) {
            currentTestCase().setResultIgnoreDetails(Level.WARN.toString());
            this.reportInfo(logger, catsResult, params);
            setResultReason(catsResult);
        } else {
            this.reportResultWarn(logger, data, catsResult.reason(), catsResult.message(), params);
        }
    }


    /**
     * Reports a warning result for a test using the provided logger, reason, message, and parameters.
     *
     * @param logger  the logger used to log result-related information
     * @param data    the fuzzing data associated with the test
     * @param reason  the reason for the warning result
     * @param message the warning message to be reported
     * @param params  additional parameters to be formatted into the message
     */
    public void reportResultWarn(PrettyLogger logger, FuzzingData data, String reason, String message, Object... params) {
        this.reportWarn(logger, message, params);
        setResultReason(reason);
    }

    private void reportError(PrettyLogger logger, CatsResultFactory.CatsResult catsResult, Object... params) {
        this.reportError(logger, catsResult.message(), params);
        setResultReason(catsResult);
    }

    /**
     * If {@code --ignoreResponseCodes} is supplied and the response code received from the service
     * is in the ignored list, the method will actually report INFO instead of ERROR.
     * If {@code --skipReportingForIgnoredCodes} is also enabled, the reporting for these ignored codes will be skipped entirely.
     *
     * @param logger  the current logger
     * @param message message to be logged
     * @param params  params needed by the message
     */
    void reportError(PrettyLogger logger, String message, Object... params) {
        this.logger.debug("Reporting error with message: {}", replaceBrackets(message, params));
        CatsTestCase testCase = currentTestCase();
        CatsResponse catsResponse = Optional.ofNullable(testCase.getResponse()).orElse(CatsResponse.empty());
        if (ignoreArguments.isNotIgnoredResponse(catsResponse) || catsResponse.exceedsExpectedResponseTime(reportingArguments.getMaxResponseTime()) || isException(catsResponse)) {
            this.logger.debug("Received response is not marked as ignored... reporting error!");
            executionStatisticsListener.increaseErrors(testCase.getContractPath());
            logger.error(message, params);
            this.recordResult(message, params, Level.ERROR.toString().toLowerCase(), logger);
            this.renderProgress(catsResponse);
        } else if (ignoreArguments.isSkipReportingForIgnoredCodes()) {
            this.logger.debug(RECEIVED_RESPONSE_IS_MARKED_AS_IGNORED_SKIPPING);
            this.skipTest(logger, "Some response elements were was marked as ignored and --skipReportingForIgnoredCodes is enabled.");
            this.recordResult(message, params, SKIP_REPORTING, logger);
        } else {
            testCase.setResultIgnoreDetails(Level.ERROR.toString());
            this.logger.debug("Received response is marked as ignored... reporting info!");
            this.reportInfo(logger, message, params);
        }
        recordAuthErrors(catsResponse);
    }


    /**
     * When {@code --printProgress} is enabled we output in the console the url that fails.
     *
     * @param catsResponse the CatsResponse object
     */
    private void renderProgress(CatsResponse catsResponse) {
        if (reportingArguments.isPrintProgress()) {
            ConsoleUtils.renderSameRowAndMoveToNextLine("+ " + catsResponse.getPath());
        }
    }

    private boolean isException(CatsResponse catsResponse) {
        return !catsResponse.isValidErrorCode();
    }

    private void recordAuthErrors(CatsResponse catsResponse) {
        if (catsResponse.getResponseCode() == 401 || catsResponse.getResponseCode() == 403) {
            executionStatisticsListener.increaseAuthErrors();
        }
    }

    private void checkForIOErrors(Exception e) {
        if (e.getCause() instanceof IOException) {
            executionStatisticsListener.increaseIoErrors();
        }
    }

    /**
     * Reports an error result for a test using the provided logger, reason, message, and parameters.
     *
     * @param logger  the logger used to log result-related information
     * @param data    the fuzzing data associated with the test
     * @param reason  the reason for the error result
     * @param message the error message to be reported
     * @param params  additional parameters to be formatted into the message
     */
    public void reportResultError(PrettyLogger logger, FuzzingData data, String reason, String message, Object... params) {
        this.reportError(logger, message, params);
        setResultReason(reason);
    }

    private void reportSkipped(PrettyLogger logger, Object... params) {
        executionStatisticsListener.increaseSkipped();
        logger.skip("Skipped due to: {}", params);
        CatsTestCase testCase = currentTestCase();
        testCase.setResultSkipped();
        testCase.setResultDetails(replaceBrackets("Skipped due to: {}", params));
    }

    void reportInfo(PrettyLogger logger, String message, Object... params) {
        CatsTestCase testCase = currentTestCase();
        CatsResponse catsResponse = Optional.ofNullable(testCase.getResponse()).orElse(CatsResponse.empty());
        if (ignoreArguments.isSkipReportingForSuccess()) {
            this.logger.debug(RECEIVED_RESPONSE_IS_MARKED_AS_IGNORED_SKIPPING);
            this.skipTest(logger, replaceBrackets("Skip reporting as --skipReportingForSuccess is enabled"));
            this.recordResult(message, params, SKIP_REPORTING, logger);
        } else if (ignoreArguments.isIgnoredResponse(catsResponse) && ignoreArguments.isSkipReportingForIgnoredCodes()) {
            this.logger.debug(RECEIVED_RESPONSE_IS_MARKED_AS_IGNORED_SKIPPING);
            this.skipTest(logger, "Some response elements were was marked as ignored and --skipReportingForIgnoredCodes is enabled.");
            this.recordResult(message, params, SKIP_REPORTING, logger);
        } else if (catsResponse.exceedsExpectedResponseTime(reportingArguments.getMaxResponseTime())) {
            this.logger.debug("Received response time exceeds --maxResponseTimeInMs: actual {}, max {}",
                    catsResponse.getResponseTimeInMs(), reportingArguments.getMaxResponseTime());
            this.reportError(logger, CatsResultFactory.createResponseTimeExceedsMax(catsResponse.getResponseTimeInMs(), reportingArguments.getMaxResponseTime()));
        } else {
            executionStatisticsListener.increaseSuccess(testCase.getContractPath());
            logger.success(message, params);
            this.recordResult(message, params, "success", logger);
        }
    }

    private void reportInfo(PrettyLogger logger, CatsResultFactory.CatsResult catsResult, Object... params) {
        this.reportInfo(logger, catsResult.message(), params);
    }

    /**
     * Reports an informational result for a test using the provided logger, message, and parameters.
     *
     * @param logger  the logger used to log result-related information
     * @param data    the fuzzing data associated with the test
     * @param message the informational message to be reported
     * @param params  additional parameters to be formatted into the message
     */
    public void reportResultInfo(PrettyLogger logger, FuzzingData data, String message, Object... params) {
        this.reportInfo(logger, message, params);
    }

    /**
     * Reports the result of a test based on the provided parameters, including response code, schema matching, and content type.
     * It also performs checks if response body matches schema.
     *
     * @param logger             the logger used to log result-related information
     * @param data               the fuzzing data associated with the test
     * @param response           the response received from the test
     * @param expectedResultCode the expected response code family
     */
    public void reportResult(PrettyLogger logger, FuzzingData data, CatsResponse response, ResponseCodeFamily expectedResultCode) {
        this.reportResult(logger, data, response, expectedResultCode, true, true);
    }

    /**
     * Reports the result of a test based on the provided parameters, including response code, schema matching, and content type.
     *
     * @param logger                      the logger used to log result-related information
     * @param data                        the fuzzing data associated with the test
     * @param response                    the response received from the test
     * @param expectedResultCode          the expected response code family
     * @param shouldMatchToResponseSchema a flag indicating whether the response should match the expected schema
     */
    public void reportResult(PrettyLogger logger, FuzzingData data, CatsResponse response, ResponseCodeFamily expectedResultCode, boolean shouldMatchToResponseSchema) {
        this.reportResult(logger, data, response, expectedResultCode, shouldMatchToResponseSchema, true);
    }

    /**
     * Reports the result of a test based on the provided parameters, including response code, schema matching, and content type.
     *
     * @param logger                      the logger used to log result-related information
     * @param data                        the fuzzing data associated with the test
     * @param response                    the response received from the test
     * @param expectedResultCode          the expected response code family
     * @param shouldMatchToResponseSchema a flag indicating whether the response should match the expected schema
     * @param shouldMatchContentType      a flag indicating whether the response content type should match the one from the OpenAPI spec
     */
    public void reportResult(PrettyLogger logger, FuzzingData data, CatsResponse response, ResponseCodeFamily expectedResultCode, boolean shouldMatchToResponseSchema, boolean shouldMatchContentType) {
        expectedResultCode = this.getExpectedResponseCodeConfiguredFor(MDC.get(FUZZER_KEY), expectedResultCode);
        boolean matchesResponseSchema = !shouldMatchToResponseSchema || this.matchesResponseSchema(response, data);
        boolean responseCodeExpected = this.isResponseCodeExpected(response, expectedResultCode);
        boolean responseCodeDocumented = this.isResponseCodeDocumented(data, response);
        boolean isResponseContentTypeMatching = !shouldMatchContentType || this.isResponseContentTypeMatching(response, data);

        this.logger.debug("matchesResponseSchema {}, responseCodeExpected {}, responseCodeDocumented {}", matchesResponseSchema, responseCodeExpected, responseCodeDocumented);
        this.storeRequestOnPostOrRemoveOnDelete(data, response);

        ResponseAssertions assertions = ResponseAssertions.builder().matchesResponseSchema(matchesResponseSchema)
                .responseCodeDocumented(responseCodeDocumented).responseCodeExpected(responseCodeExpected).
                responseCodeUnimplemented(ResponseCodeFamily.isUnimplemented(response.getResponseCode()))
                .matchesContentType(isResponseContentTypeMatching).build();

        if (assertions.isNotMatchingContentType() && !ignoreArguments.isIgnoreResponseContentTypeCheck()) {
            this.logger.debug("Response content type not matching contract");
            CatsResultFactory.CatsResult contentTypeNotMatching = CatsResultFactory.createNotMatchingContentType(data.getContentTypesByResponseCode(response.responseCodeAsString()), response.getResponseContentType());
            this.reportResultWarn(logger, data, contentTypeNotMatching.reason(), contentTypeNotMatching.message());
        } else if (assertions.isResponseCodeExpectedAndDocumentedAndMatchesResponseSchema()) {
            this.logger.debug("Response code expected and documented and matches response schema");
            this.reportInfo(logger, CatsResultFactory.createExpectedResponse(response.responseCodeAsString()));
        } else if (assertions.isResponseCodeExpectedAndDocumentedButDoesntMatchResponseSchema()) {
            this.logger.debug("Response code expected and documented and but doesn't match response schema");
            this.reportWarnOrInfoBasedOnCheck(logger, data, CatsResultFactory.createNotMatchingResponseSchema(response.responseCodeAsString()), ignoreArguments.isIgnoreResponseBodyCheck());
        } else if (assertions.isResponseCodeExpectedButNotDocumented()) {
            this.logger.debug("Response code expected but not documented");
            this.reportWarnOrInfoBasedOnCheck(logger, data,
                    CatsResultFactory.createUndocumentedResponseCode(response.responseCodeAsString(), String.valueOf(expectedResultCode.allowedResponseCodes()), String.valueOf(data.getResponseCodes())),
                    ignoreArguments.isIgnoreResponseCodeUndocumentedCheck());
        } else if (assertions.isResponseCodeDocumentedButNotExpected()) {
            if (isNotFound(response)) {
                this.logger.debug("NOT_FOUND response");
                this.reportError(logger, CatsResultFactory.createNotFound());
            } else if (assertions.isResponseCodeUnimplemented()) {
                this.logger.debug("Response code unimplemented");
                CatsResultFactory.CatsResult notImplementedResult = CatsResultFactory.createNotImplemented();
                this.reportResultWarn(logger, data, notImplementedResult.reason(), notImplementedResult.message());
            } else {
                this.logger.debug("Response code documented but not expected");
                this.reportError(logger, CatsResultFactory.createUnexpectedResponseCode(response.responseCodeAsString(), expectedResultCode.allowedResponseCodes().toString()));
            }
        } else if (isNotFound(response)) {
            this.logger.debug("NOT_FOUND response");
            this.reportError(logger, CatsResultFactory.createNotFound());
        } else {
            this.logger.debug("Unexpected behaviour");
            this.reportError(logger, CatsResultFactory.createUnexpectedBehaviour(response.responseCodeAsString(), expectedResultCode.allowedResponseCodes().toString()));
        }
    }

    private boolean isResponseContentTypeMatching(CatsResponse response, FuzzingData data) {
        boolean noContentTypeDefinedForResponseCode = data.getResponseContentTypes().get(response.responseCodeAsString()) == null;
        boolean responseDoesNotHaveContentType = response.getResponseContentType() == null;
        boolean responseContentTypeDefined = data.getContentTypesByResponseCode(response.responseCodeAsString())
                .stream()
                .anyMatch(contentType -> areContentTypesEquivalent(response.getResponseContentType(), contentType));
        boolean unknownContentType = response.isUnknownContentType();

        return (noContentTypeDefinedForResponseCode && responseDoesNotHaveContentType) || responseContentTypeDefined || unknownContentType;
    }

    static boolean areContentTypesEquivalent(String firstContentType, String secondContentType) {
        MediaType firstMediaType = MediaType.parse(Optional.ofNullable(firstContentType).orElse(CatsResponse.unknownContentType())).withoutParameters();
        MediaType secondMediaType = MediaType.parse(Optional.ofNullable(secondContentType).orElse(CatsResponse.unknownContentType())).withoutParameters();

        return firstMediaType.is(secondMediaType) || secondMediaType.is(firstMediaType) || (firstMediaType.type().equalsIgnoreCase(secondMediaType.type()) &&
                (firstMediaType.subtype().endsWith(secondMediaType.subtype()) || secondMediaType.subtype().endsWith(firstMediaType.subtype())));
    }

    private void storeRequestOnPostOrRemoveOnDelete(FuzzingData data, CatsResponse response) {
        if (data.getMethod() == HttpMethod.POST && ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            logger.star("POST method for path {} returned successfully {}. Storing result for DELETE endpoints...", data.getPath(), response.responseCodeAsString());
            Deque<String> existingPosts = globalContext.getPostSuccessfulResponses().getOrDefault(data.getPath(), new ArrayDeque<>());
            existingPosts.add(response.getBody());
            globalContext.getPostSuccessfulResponses().put(data.getPath(), existingPosts);
        } else if (data.getMethod() == HttpMethod.DELETE && ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            logger.star("Successful DELETE. Removing top POST request from the store...");
            globalContext.getPostSuccessfulResponses().getOrDefault(data.getPath().substring(0, data.getPath().lastIndexOf("/")), new ArrayDeque<>()).poll();
        }
    }

    private boolean isNotFound(CatsResponse response) {
        return response.getResponseCode() == 404;
    }

    private boolean isResponseCodeDocumented(FuzzingData data, CatsResponse response) {
        Set<String> responseCodes = Optional.ofNullable(data.getResponseCodes()).orElse(Collections.emptySet());
        return responseCodes.contains(response.responseCodeAsString()) ||
                isNotTypicalDocumentedResponseCode(response) ||
                responseMatchesDocumentedRange(response.responseCodeAsResponseRange(), responseCodes);
    }

    private boolean responseMatchesDocumentedRange(String receivedResponseCode, Set<String> documentedResponseCodes) {
        return documentedResponseCodes.stream().anyMatch(code -> code.equalsIgnoreCase(receivedResponseCode));
    }

    /**
     * Skips the current test, adds the skip reason to the expected results, and reports the test as skipped using the provided logger.
     *
     * @param logger     the logger used to log skip-related information
     * @param skipReason the reason for skipping the test
     */
    public void skipTest(PrettyLogger logger, String skipReason) {
        this.addExpectedResult(logger, skipReason);
        this.reportSkipped(logger, skipReason);
    }

    /**
     * Checks if a fuzzed field is not a discriminator based on the configured discriminators in the global context.
     *
     * @param fuzzedField the fuzzed field to check
     * @return true if the fuzzed field is not a discriminator, false otherwise
     */
    public boolean isFieldNotADiscriminator(String fuzzedField) {
        return globalContext.getDiscriminators().stream().noneMatch(discriminator -> fuzzedField.endsWith(discriminator.getPropertyName()));
    }

    /**
     * Returns the expected HTTP response code from the --fuzzConfig file
     *
     * @param fuzzer       the name of the fuzzer
     * @param defaultValue default value when property is not found
     * @return the value of the property if found or null otherwise
     */
    public ResponseCodeFamily getExpectedResponseCodeConfiguredFor(String fuzzer, ResponseCodeFamily defaultValue) {
        String keyToLookup = fuzzer + "." + "expectedResponseCode";
        String valueFound = globalContext.getExpectedResponseCodeConfigured(keyToLookup);
        logger.debug("Configuration key {}, value {}", keyToLookup, valueFound);

        if (valueFound == null) {
            return defaultValue;
        }
        List<String> responseCodes = Arrays.stream(valueFound.split(",", -1))
                .map(String::trim)
                .filter(item -> item.length() == 3)
                .toList();
        return new ResponseCodeFamilyDynamic(responseCodes);
    }

    private void recordResult(String message, Object[] params, String result, PrettyLogger logger) {
        CatsTestCase testCase = currentTestCase();
        testCase.setResult(result);
        testCase.setResultDetails(replaceBrackets(message, params));
        logger.star("{}, Path {}, HttpMethod {}, Result {}", testCase.getTestId(), testCase.getPath(), Optional.ofNullable(testCase.getRequest()).orElse(CatsRequest.empty()).getHttpMethod(), result);
        storeSuccessfulDelete(testCase);
    }

    void storeSuccessfulDelete(CatsTestCase testCase) {
        if (ResponseCodeFamily.is2xxCode(testCase.getResponse().getResponseCode()) && HttpMethod.DELETE.name().equalsIgnoreCase(testCase.getRequest().getHttpMethod())) {
            globalContext.getSuccessfulDeletes().add(testCase.getRequest().getUrl());
            logger.note("Storing successful DELETE: {}", testCase.getRequest().getUrl());
        }
    }

    /**
     * The response code is expected if the response code received from the server matches the Cats test case expectations.
     * There is also a particular case when we fuzz GET requests, and we reach unimplemented endpoints. This is why we also test for 501
     *
     * @param response           response received from the service
     * @param expectedResultCode what is CATS expecting in this scenario
     * @return {@code true} if the response matches CATS expectations and {@code false} otherwise
     */
    private boolean isResponseCodeExpected(CatsResponse response, ResponseCodeFamily expectedResultCode) {
        return expectedResultCode.matchesAllowedResponseCodes(response.responseCodeAsString());
    }

    private boolean matchesResponseSchema(CatsResponse response, FuzzingData data) {
        try {
            List<String> responses = this.getExpectedResponsesByResponseCode(response, data);

            return isNullResponse(response)
                    || isResponseEmpty(response, responses)
                    || isResponseContentTypeNotMatchable(response)
                    || isNotTypicalDocumentedResponseCode(response)
                    || isEmptyArray(response.getJsonBody())
                    || isActualResponseMatchingDocumentedResponses(response, responses);
        } catch (Exception e) {
            logger.debug("Something happened while matching response schema!", e);
            //if something happens during json parsing we consider it doesn't match schema
            return false;
        }
    }

    private boolean isNullResponse(CatsResponse response) {
        return response.getJsonBody() == null || response.getBody() == null;
    }

    private boolean isResponseContentTypeNotMatchable(CatsResponse response) {
        return CONTENT_TYPE_DONT_MATCH_SCHEMA
                .stream()
                .anyMatch(dontMatchContentType -> areContentTypesEquivalent(dontMatchContentType, response.getResponseContentType()));
    }

    private boolean isEmptyArray(JsonElement jsonElement) {
        return jsonElement.isJsonArray() && isEmptyBody(jsonElement.toString());
    }

    private List<String> getExpectedResponsesByResponseCode(CatsResponse response, FuzzingData data) {
        Map<String, List<String>> responsesMap = Optional.ofNullable(data.getResponses()).orElse(Collections.emptyMap());
        List<String> responses = responsesMap.get(response.responseCodeAsString());

        if (CollectionUtils.isEmpty(responses)) {
            return responsesMap.getOrDefault(response.responseCodeAsResponseRange(),
                    responsesMap.get(response.responseCodeAsResponseRange().toLowerCase(Locale.ROOT)));
        }

        return responses;
    }

    private boolean isActualResponseMatchingDocumentedResponses(CatsResponse response, List<String> responses) {
        return responses != null && responses.stream().anyMatch(responseSchema -> matchesElement(responseSchema, response.getJsonBody()))
                && (isFuzzedFieldPresentInResponse(response) || !isErrorResponse(response));
    }

    private boolean isErrorResponse(CatsResponse response) {
        return ResponseCodeFamilyPredefined.FOURXX.matchesAllowedResponseCodes(response.responseCodeAsString());
    }

    private boolean isFuzzedFieldPresentInResponse(CatsResponse response) {
        return response.getFuzzedField() == null ||
                response.getBody()
                        .replaceAll("[-_\\s]+", "")
                        .toLowerCase(Locale.ROOT)
                        .contains(response.getFuzzedField()
                                .replaceAll("[-_#\\s]+", "")
                                .toLowerCase(Locale.ROOT));
    }

    private boolean isNotTypicalDocumentedResponseCode(CatsResponse response) {
        return NOT_NECESSARILY_DOCUMENTED.contains(response.responseCodeAsString());
    }

    private boolean isResponseEmpty(CatsResponse response, List<String> responses) {
        return (responses == null || responses.isEmpty()) && isEmptyBody(response.getBody());
    }

    private boolean isEmptyBody(String body) {
        boolean isEmptyString = body.trim().isEmpty();
        boolean isEmptyArray = body.trim().equalsIgnoreCase("[]");
        boolean isEmptyJson = body.trim().equalsIgnoreCase("{}");

        return isEmptyString || isEmptyArray || isEmptyJson;
    }

    private boolean matchesElement(String responseSchema, JsonElement element) {
        if (element.isJsonArray()) {
            return matchesArrayElement(responseSchema, element);
        }

        return matchesSingleElement(responseSchema, element, "ROOT");
    }

    private boolean matchesArrayElement(String responseSchema, JsonElement element) {
        JsonArray jsonArray = ((JsonArray) element);

        if (jsonArray.isEmpty() && JsonParser.parseString(responseSchema).isJsonArray()) {
            return true;
        } else if (jsonArray.isEmpty()) {
            return false;
        }

        JsonElement firstElement = jsonArray.get(0);
        return matchesSingleElement(responseSchema, firstElement, "ROOT");
    }

    private boolean matchesSingleElement(String responseSchema, JsonElement element, String name) {
        if (element.isJsonObject() && globalContext.getAdditionalProperties().containsKey(name)) {
            return true;
        }
        if (doesNotHaveAResponseSchema(responseSchema)) {
            return true;
        }
        if (!element.isJsonObject()) {
            return responseSchema.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT));
        }

        boolean matches = true;
        for (Map.Entry<String, JsonElement> inner : element.getAsJsonObject().entrySet()) {
            matches = matches && matchesSingleElement(responseSchema, inner.getValue(), inner.getKey());
        }

        return matches;
    }

    private static boolean doesNotHaveAResponseSchema(String responseSchema) {
        return responseSchema == null || responseSchema.isEmpty();
    }

    private CatsTestCase currentTestCase() {
        return testCaseMap.get(MDC.get(ID));
    }

    public void recordError(String error) {
        globalContext.recordError(error);
    }

    @Builder
    static class ResponseAssertions {
        private final boolean matchesResponseSchema;
        private final boolean responseCodeExpected;
        private final boolean responseCodeDocumented;
        private final boolean responseCodeUnimplemented;
        private final boolean matchesContentType;

        private boolean isNotMatchingContentType() {
            return !matchesContentType;
        }

        private boolean isResponseCodeExpectedAndDocumentedAndMatchesResponseSchema() {
            return matchesResponseSchema && responseCodeDocumented && responseCodeExpected;
        }

        private boolean isResponseCodeExpectedAndDocumentedButDoesntMatchResponseSchema() {
            return !matchesResponseSchema && responseCodeDocumented && responseCodeExpected;
        }

        private boolean isResponseCodeDocumentedButNotExpected() {
            return responseCodeDocumented && !responseCodeExpected;
        }

        private boolean isResponseCodeExpectedButNotDocumented() {
            return responseCodeExpected && !responseCodeDocumented;
        }

        private boolean isResponseCodeUnimplemented() {
            return responseCodeUnimplemented;
        }
    }
}
