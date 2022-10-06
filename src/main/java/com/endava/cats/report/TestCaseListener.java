package com.endava.cats.report;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.report.CatsResult;
import com.endava.cats.model.report.CatsTestCase;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fusesource.jansi.Ansi;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.util.CollectionUtils;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * This class exposes methods to record the progress of a test case
 */
@ApplicationScoped
@DryRun
public class TestCaseListener {
    public static final String ID = "id";
    public static final String FUZZER_KEY = "fuzzerKey";
    public static final String FUZZER = "fuzzer";
    protected static final String ID_ANSI = "id_ansi";
    protected static final AtomicInteger TEST = new AtomicInteger(0);
    private static final String SEPARATOR = StringUtils.repeat("-", 100);
    private static final String DEFAULT_ERROR = "####";
    private static final List<String> NOT_NECESSARILY_DOCUMENTED = Arrays.asList("406", "415", "414");
    public static final String RECEIVED_RESPONSE_IS_MARKED_AS_IGNORED_SKIPPING = "Received response is marked as ignored... skipping!";
    protected final Map<String, CatsTestCase> testCaseMap = new HashMap<>();
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(TestCaseListener.class);
    private final ExecutionStatisticsListener executionStatisticsListener;
    private final TestCaseExporter testCaseExporter;
    private final CatsGlobalContext globalContext;
    private final IgnoreArguments ignoreArguments;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String appVersion;
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "cats")
    String appName;
    @ConfigProperty(name = "app.timestamp", defaultValue = "1-1-1")
    String appBuildTime;

    public TestCaseListener(CatsGlobalContext catsGlobalContext, ExecutionStatisticsListener er, Instance<TestCaseExporter> exporters, IgnoreArguments filterArguments, ReportingArguments reportingArguments) {
        this.executionStatisticsListener = er;
        this.testCaseExporter = exporters.stream()
                .filter(exporter -> exporter.reportFormat() == reportingArguments.getReportFormat())
                .findFirst()
                .orElseThrow();
        this.ignoreArguments = filterArguments;
        this.globalContext = catsGlobalContext;
    }

    private static String replaceBrackets(String message, Object... params) {
        for (Object obj : params) {
            message = message.replaceFirst(Pattern.quote("{}"), Matcher.quoteReplacement(String.valueOf(obj)));
        }

        return message;
    }

    public void beforeFuzz(Class<?> fuzzer) {
        String clazz = ConsoleUtils.removeTrimSanitize(fuzzer.getSimpleName()).replaceAll("[a-z]", "");
        MDC.put(FUZZER, ConsoleUtils.centerWithAnsiColor(clazz, CatsUtil.FUZZER_KEY_DEFAULT.length(), Ansi.Color.MAGENTA));
        MDC.put(FUZZER_KEY, ConsoleUtils.removeTrimSanitize(fuzzer.getSimpleName()));
    }

    public void afterFuzz() {
        MDC.put(FUZZER, CatsUtil.FUZZER_KEY_DEFAULT);
        MDC.put(FUZZER_KEY, CatsUtil.FUZZER_KEY_DEFAULT);
    }

    public void createAndExecuteTest(PrettyLogger externalLogger, Fuzzer fuzzer, Runnable s) {
        this.startTestCase();
        try {
            s.run();
        } catch (Exception e) {
            setDefaultPathAndMethod();
            this.reportResultError(externalLogger, FuzzingData.builder().path(DEFAULT_ERROR).build(), CatsResult.EXCEPTION.
                    withDocumentedResponseCodes(Optional.ofNullable(e.getMessage()).orElse("")).withExpectedResponseCodes(fuzzer.getClass().getSimpleName()).getMessage());
            setResultReason(CatsResult.EXCEPTION);
            externalLogger.error("Exception while processing: {}", e.getMessage());
            externalLogger.debug("Detailed stacktrace", e);
        }
        this.endTestCase();
        logger.info("{} {}", SEPARATOR, "\n");
    }

    private void setDefaultPathAndMethod() {
        CatsTestCase testCase = testCaseMap.get(MDC.get(ID));
        if (testCase.getPath() == null) {
            testCase.setPath(DEFAULT_ERROR);
        }
        if (testCase.getRequest() == null) {
            testCase.setRequest(CatsRequest.builder().httpMethod(DEFAULT_ERROR).build());
        }
    }

    private void startTestCase() {
        String testId = "Test " + TEST.incrementAndGet();
        MDC.put(ID, testId);
        MDC.put(ID_ANSI, ConsoleUtils.centerWithAnsiColor(testId, 10, Ansi.Color.MAGENTA));

        testCaseMap.put(testId, new CatsTestCase());
        testCaseMap.get(testId).setTestId(testId);
    }

    public void addScenario(PrettyLogger logger, String scenario, Object... params) {
        logger.info(scenario, params);
        testCaseMap.get(MDC.get(ID)).setScenario(replaceBrackets(scenario, params));
    }

    public void addExpectedResult(PrettyLogger logger, String expectedResult, Object... params) {
        logger.info(expectedResult, params);
        testCaseMap.get(MDC.get(ID)).setExpectedResult(replaceBrackets(expectedResult, params));
    }

    public void addPath(String path) {
        testCaseMap.get(MDC.get(ID)).setPath(path);
    }

    public void addRequest(CatsRequest request) {
        if (testCaseMap.get(MDC.get(ID)).getRequest() == null) {
            testCaseMap.get(MDC.get(ID)).setRequest(request);
        }
    }

    public void addResponse(CatsResponse response) {
        if (testCaseMap.get(MDC.get(ID)).getResponse() == null) {
            testCaseMap.get(MDC.get(ID)).setResponse(response);
        }
    }

    public void addFullRequestPath(String fullRequestPath) {
        testCaseMap.get(MDC.get(ID)).setFullRequestPath(fullRequestPath);
    }

    private void endTestCase() {
        testCaseMap.get(MDC.get(ID)).setFuzzer(MDC.get(FUZZER_KEY));
        if (testCaseMap.get(MDC.get(ID)).isNotSkipped()) {
            testCaseExporter.writeTestCase(testCaseMap.get(MDC.get(ID)));
        }
        MDC.remove(ID);
        MDC.put(ID_ANSI, CatsUtil.TEST_KEY_DEFAULT);
    }

    public void startSession() {
        MDC.put(ID_ANSI, CatsUtil.TEST_KEY_DEFAULT);
        MDC.put(FUZZER, CatsUtil.FUZZER_KEY_DEFAULT);
        MDC.put(FUZZER_KEY, CatsUtil.FUZZER_KEY_DEFAULT);

        logger.start("Starting {}, version {}, build-time {} UTC", ansi().fg(Ansi.Color.GREEN).a(appName.toUpperCase()), ansi().fg(Ansi.Color.GREEN).a(appVersion), ansi().fg(Ansi.Color.GREEN).a(appBuildTime).reset());
        logger.note("{}", ansi().fgGreen().a("Processing configuration...").reset());
    }

    public void initReportingPath() throws IOException {
        testCaseExporter.initPath();
    }

    public void endSession() {
        testCaseExporter.writeSummary(testCaseMap, executionStatisticsListener);
        testCaseExporter.writeHelperFiles();
        testCaseExporter.writePerformanceReport(testCaseMap);
        testCaseExporter.printExecutionDetails(executionStatisticsListener);
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
    public void reportWarn(PrettyLogger logger, String message, Object... params) {
        this.logger.debug("Reporting warn with message: {}", replaceBrackets(message, params));
        CatsResponse catsResponse = Optional.ofNullable(testCaseMap.get(MDC.get(TestCaseListener.ID)).getResponse()).orElse(CatsResponse.empty());
        if (ignoreArguments.isNotIgnoredResponse(catsResponse)) {
            this.logger.debug("Received response is not marked as ignored... reporting warn!");
            executionStatisticsListener.increaseWarns();
            logger.warning(message, params);
            this.recordResult(message, params, Level.WARN.toString().toLowerCase(), logger);
        } else if (ignoreArguments.isSkipReportingForIgnoredCodes()) {
            this.logger.debug(RECEIVED_RESPONSE_IS_MARKED_AS_IGNORED_SKIPPING);
            this.skipTest(logger, replaceBrackets("Some response elements were marked as ignored and --skipReportingForIgnoredCodes is enabled."));
        } else {
            this.logger.debug("Received response is marked as ignored... reporting info!");
            this.reportInfo(logger, message, params);
        }
    }


    private void reportWarnOrInfoBasedOnCheck(PrettyLogger logger, CatsResult catsResult, boolean ignoreCheck, Object... params) {
        if (ignoreCheck) {
            this.reportInfo(logger, catsResult, params);
            setResultReason(catsResult);
        } else {
            this.reportWarn(logger, catsResult, params);
        }
    }

    private void reportWarn(PrettyLogger logger, CatsResult catsResult, Object... params) {
        this.reportWarn(logger, catsResult.getMessage(), params);
        setResultReason(catsResult);
    }

    private void setResultReason(CatsResult catsResult) {
        CatsTestCase testCase = testCaseMap.get(MDC.get(ID));
        testCase.setResultReason(catsResult.getReason());
    }

    private void setResultReason(String reason) {
        CatsTestCase testCase = testCaseMap.get(MDC.get(ID));
        testCase.setResultReason(reason);
    }

    public void reportError(PrettyLogger logger, CatsResult catsResult, Object... params) {
        this.reportError(logger, catsResult.getMessage(), params);
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
    public void reportError(PrettyLogger logger, String message, Object... params) {
        this.logger.debug("Reporting error with message: {}", replaceBrackets(message, params));
        CatsResponse catsResponse = Optional.ofNullable(testCaseMap.get(MDC.get(ID)).getResponse()).orElse(CatsResponse.empty());
        this.addRequest(CatsRequest.empty());
        this.addResponse(CatsResponse.empty());
        if (ignoreArguments.isNotIgnoredResponse(catsResponse)) {
            this.logger.debug("Received response is not marked as ignored... reporting error!");
            executionStatisticsListener.increaseErrors();
            logger.error(message, params);
            this.recordResult(message, params, Level.ERROR.toString().toLowerCase(), logger);
        } else if (ignoreArguments.isSkipReportingForIgnoredCodes()) {
            this.logger.debug(RECEIVED_RESPONSE_IS_MARKED_AS_IGNORED_SKIPPING);
            this.skipTest(logger, "Some response elements were was marked as ignored and --skipReportingForIgnoredCodes is enabled.");
        } else {
            this.logger.debug("Received response is marked as ignored... reporting info!");
            this.reportInfo(logger, message, params);
        }
    }

    private void reportSkipped(PrettyLogger logger, Object... params) {
        executionStatisticsListener.increaseSkipped();
        logger.skip("Skipped due to: {}", params);
        recordResult("Skipped due to: {}", params, "skipped", logger);
    }

    public void reportInfo(PrettyLogger logger, String message, Object... params) {
        CatsResponse catsResponse = Optional.ofNullable(testCaseMap.get(MDC.get(ID)).getResponse()).orElse(CatsResponse.empty());

        if (ignoreArguments.isIgnoredResponse(catsResponse) && ignoreArguments.isSkipReportingForIgnoredCodes()) {
            this.logger.debug(RECEIVED_RESPONSE_IS_MARKED_AS_IGNORED_SKIPPING);
            this.skipTest(logger, "Some response elements were was marked as ignored and --skipReportingForIgnoredCodes is enabled.");
        } else {
            executionStatisticsListener.increaseSuccess();
            logger.success(message, params);
            this.recordResult(message, params, "success", logger);
        }
    }

    private void reportInfo(PrettyLogger logger, CatsResult catsResult, Object... params) {
        this.reportInfo(logger, catsResult.getMessage(), params);
    }

    public void reportResultError(PrettyLogger logger, FuzzingData data, String message, Object... params) {
        this.reportError(logger, message, params);
    }

    public void reportResultError(PrettyLogger logger, FuzzingData data, String reason, String message, Object... params) {
        this.reportError(logger, message, params);
        setResultReason(reason);
    }

    public void reportResultInfo(PrettyLogger logger, FuzzingData data, String message, Object... params) {
        this.reportInfo(logger, message, params);
    }

    public void reportResultWarn(PrettyLogger logger, FuzzingData data, String message, Object... params) {
        this.reportWarn(logger, message, params);
    }

    public void reportResultWarn(PrettyLogger logger, FuzzingData data, String reason, String message, Object... params) {
        this.reportWarn(logger, message, params);
        setResultReason(reason);
    }

    public void reportResult(PrettyLogger logger, FuzzingData data, CatsResponse response, ResponseCodeFamily expectedResultCode) {
        this.reportResult(logger, data, response, expectedResultCode, true);
    }

    public void reportResult(PrettyLogger logger, FuzzingData data, CatsResponse response, ResponseCodeFamily expectedResultCode, boolean shouldMatchToResponseSchema) {
        boolean matchesResponseSchema = !shouldMatchToResponseSchema || this.matchesResponseSchema(response, data);
        boolean responseCodeExpected = this.isResponseCodeExpected(response, expectedResultCode);
        boolean responseCodeDocumented = this.isResponseCodeDocumented(data, response);

        this.logger.debug("matchesResponseSchema {}, responseCodeExpected {}, responseCodeDocumented {}", matchesResponseSchema, responseCodeExpected, responseCodeDocumented);
        this.storeRequestOnPostOrRemoveOnDelete(data, response);

        ResponseAssertions assertions = ResponseAssertions.builder().matchesResponseSchema(matchesResponseSchema)
                .responseCodeDocumented(responseCodeDocumented).responseCodeExpected(responseCodeExpected).
                responseCodeUnimplemented(ResponseCodeFamily.isUnimplemented(response.getResponseCode())).build();
        if (isNotFound(response)) {
            this.logger.debug("NOT_FOUND response");
            this.reportError(logger, CatsResult.NOT_FOUND);
        } else if (assertions.isResponseCodeExpectedAndDocumentedAndMatchesResponseSchema()) {
            this.logger.debug("Response code expected and documented and matches response schema");
            this.reportInfo(logger, CatsResult.OK.withResponseCode(response.responseCodeAsString()));
        } else if (assertions.isResponseCodeExpectedAndDocumentedButDoesntMatchResponseSchema()) {
            this.logger.debug("Response code expected and documented and but doesn't match response schema");
            this.reportWarnOrInfoBasedOnCheck(logger, CatsResult.NOT_MATCHING_RESPONSE_SCHEMA.withResponseCode(response.responseCodeAsString()), ignoreArguments.isIgnoreResponseBodyCheck());
        } else if (assertions.isResponseCodeExpectedButNotDocumented()) {
            this.logger.debug("Response code expected but not documented");
            this.reportWarnOrInfoBasedOnCheck(logger, CatsResult.UNDOCUMENTED_RESPONSE_CODE
                    .withResponseCode(response.responseCodeAsString())
                    .withDocumentedResponseCodes(data.getResponseCodes().toString())
                    .withExpectedResponseCodes(expectedResultCode.allowedResponseCodes().toString()), ignoreArguments.isIgnoreResponseCodeUndocumentedCheck());
        } else if (assertions.isResponseCodeDocumentedButNotExpected()) {
            this.logger.debug("Response code documented but not expected");
            this.reportError(logger, CatsResult.UNEXPECTED_RESPONSE_CODE.withResponseCode(response.responseCodeAsString()).withExpectedResponseCodes(expectedResultCode.allowedResponseCodes().toString()));
        } else if (assertions.isResponseCodeUnimplemented()) {
            this.logger.debug("Response code unimplemented");
            this.reportWarn(logger, CatsResult.NOT_IMPLEMENTED);
        } else {
            this.logger.debug("Unexpected behaviour");
            this.reportError(logger, CatsResult.UNEXPECTED_BEHAVIOUR.withResponseCode(response.responseCodeAsString()).withExpectedResponseCodes(expectedResultCode.allowedResponseCodes().toString()));
        }
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
            logger.note("Storing DELETE request for next fuzzers {}", response.getPath());
        }
    }

    private boolean isNotFound(CatsResponse response) {
        return response.getResponseCode() == 404;
    }

    private boolean isResponseCodeDocumented(FuzzingData data, CatsResponse response) {
        return data.getResponseCodes().contains(response.responseCodeAsString()) ||
                isNotTypicalDocumentedResponseCode(response) ||
                responseMatchesDocumentedRange(response.responseCodeAsResponseRange(), data.getResponseCodes());
    }

    private boolean responseMatchesDocumentedRange(String receivedResponseCode, Set<String> documentedResponseCodes) {
        return documentedResponseCodes.stream().anyMatch(code -> code.equalsIgnoreCase(receivedResponseCode));
    }

    public void skipTest(PrettyLogger logger, String skipReason) {
        this.addExpectedResult(logger, "Test will be skipped!");
        this.addRequest(CatsRequest.empty());
        this.addResponse(CatsResponse.empty());
        this.reportSkipped(logger, skipReason);
    }

    public boolean isFieldNotADiscriminator(String fuzzedField) {
        return globalContext.getDiscriminators().stream().noneMatch(discriminator -> fuzzedField.endsWith(discriminator.getPropertyName()));
    }

    public void recordResult(String message, Object[] params, String success, PrettyLogger logger) {
        CatsTestCase testCase = testCaseMap.get(MDC.get(ID));
        testCase.setResult(success);
        testCase.setResultDetails(replaceBrackets(message, params));
        logger.star("{}, Path {}, HttpMethod {}, Result {}", testCase.getTestId(), testCase.getPath(), Optional.ofNullable(testCase.getRequest()).orElse(CatsRequest.empty()).getHttpMethod(), success);
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
        return expectedResultCode.allowedResponseCodes().contains(String.valueOf(response.responseCodeAsString()));
    }

    private boolean matchesResponseSchema(CatsResponse response, FuzzingData data) {
        JsonReader reader = new JsonReader(new StringReader(response.getBody()));
        reader.setLenient(true);
        JsonElement jsonElement = JsonParser.parseReader(reader);
        List<String> responses = this.getExpectedResponsesByResponseCode(response, data);
        return isActualResponseMatchingDocumentedResponses(response, jsonElement, responses)
                || isResponseEmpty(response, responses)
                || isNotTypicalDocumentedResponseCode(response);
    }

    private List<String> getExpectedResponsesByResponseCode(CatsResponse response, FuzzingData data) {
        List<String> responses = data.getResponses().get(response.responseCodeAsString());

        if (CollectionUtils.isEmpty(responses)) {
            return data.getResponses().getOrDefault(response.responseCodeAsResponseRange(),
                    data.getResponses().get(response.responseCodeAsResponseRange().toLowerCase(Locale.ROOT)));
        }

        return responses;
    }

    private boolean isActualResponseMatchingDocumentedResponses(CatsResponse response, JsonElement jsonElement, List<String> responses) {
        return responses != null && responses.stream().anyMatch(responseSchema -> matchesElement(responseSchema, jsonElement))
                && ((isErrorResponse(response) && isFuzzedFieldPresentInResponse(response)) || isNotErrorResponse(response));
    }

    private boolean isErrorResponse(CatsResponse response) {
        return ResponseCodeFamily.FOURXX.allowedResponseCodes().contains(response.responseCodeAsString());
    }

    private boolean isNotErrorResponse(CatsResponse response) {
        return !isErrorResponse(response);
    }

    private boolean isFuzzedFieldPresentInResponse(CatsResponse response) {
        return response.getFuzzedField() == null || response.getBody().contains(response.getFuzzedField());
    }

    private boolean isNotTypicalDocumentedResponseCode(CatsResponse response) {
        return NOT_NECESSARILY_DOCUMENTED.contains(response.responseCodeAsString());
    }

    private boolean isResponseEmpty(CatsResponse response, List<String> responses) {
        return (responses == null || responses.isEmpty()) && isEmptyBody(response.getBody());
    }

    private boolean isEmptyBody(String body) {
        return body.trim().isEmpty() || body.trim().equalsIgnoreCase("[]") || body.trim().equalsIgnoreCase("{}");
    }

    private boolean matchesElement(String responseSchema, JsonElement element) {
        if (element.isJsonArray()) {
            return matchesArrayElement(responseSchema, element);
        }

        return matchesSingleElement(responseSchema, element, "ROOT");
    }

    private boolean matchesArrayElement(String responseSchema, JsonElement element) {
        JsonArray jsonArray = ((JsonArray) element);

        if (jsonArray.size() == 0 && JsonParser.parseString(responseSchema).isJsonArray()) {
            return true;
        } else if (jsonArray.size() == 0) {
            return false;
        }

        JsonElement firstElement = jsonArray.get(0);
        return matchesSingleElement(responseSchema, firstElement, "ROOT");
    }

    private boolean matchesSingleElement(String responseSchema, JsonElement element, String name) {
        boolean result = true;
        if (element.isJsonObject() && globalContext.getAdditionalProperties().contains(name)) {
            return true;
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> inner : element.getAsJsonObject().entrySet()) {
                result = result && matchesSingleElement(responseSchema, inner.getValue(), inner.getKey());
            }
        } else {
            return responseSchema != null && responseSchema.contains(name);
        }
        return result || responseSchema == null || responseSchema.isEmpty();
    }

    @Builder
    static class ResponseAssertions {
        private final boolean matchesResponseSchema;
        private final boolean responseCodeExpected;
        private final boolean responseCodeDocumented;
        private final boolean responseCodeUnimplemented;

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
