package com.endava.cats.report;

import com.endava.cats.CatsMain;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.report.CatsResult;
import com.endava.cats.model.report.CatsTestCase;
import com.endava.cats.util.ConsoleUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.Ansi;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * This class exposes methods to record the progress of a test case
 */
@Component
public class TestCaseListener {

    protected static final String ID = "id";
    protected static final String ID_ANSI = "id_ansi";
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(TestCaseListener.class);
    private static final String SEPARATOR = StringUtils.repeat("-", 100);
    private static final List<String> NOT_NECESSARILY_DOCUMENTED = Arrays.asList("406", "415", "414");
    protected final Map<String, CatsTestCase> testCaseMap = new HashMap<>();
    private final ExecutionStatisticsListener executionStatisticsListener;
    private final TestCaseExporter testCaseExporter;
    private final BuildProperties buildProperties;


    @Autowired
    public TestCaseListener(ExecutionStatisticsListener er, TestCaseExporter tce, BuildProperties buildProperties) {
        this.executionStatisticsListener = er;
        this.testCaseExporter = tce;
        this.buildProperties = buildProperties;
    }

    private static String replaceBrackets(String message, Object... params) {
        for (Object obj : params) {
            message = message.replaceFirst(Pattern.quote("{}"), Matcher.quoteReplacement(String.valueOf(obj)));
        }

        return message;
    }

    public void createAndExecuteTest(PrettyLogger externalLogger, Fuzzer fuzzer, Runnable s) {
        String testId = "Test " + CatsMain.TEST.incrementAndGet();
        MDC.put(ID, testId);
        MDC.put(ID_ANSI, ConsoleUtils.centerWithAnsiColor(testId, 10, Ansi.Color.MAGENTA));
        this.startTestCase();
        try {
            s.run();
        } catch (Exception e) {
            this.reportError(externalLogger, CatsResult.EXCEPTION, fuzzer.getClass().getSimpleName(), e.getMessage());
            externalLogger.error("Exception while processing!", e);
        }
        this.endTestCase();
        LOGGER.info("{} {}", SEPARATOR, "\n");
        MDC.put(ID, null);
        MDC.put(ID_ANSI, null);

    }

    private void startTestCase() {
        testCaseMap.put(MDC.get(ID), new CatsTestCase());
        testCaseMap.get(MDC.get(ID)).setTestId(MDC.get(ID));
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
        testCaseMap.get(MDC.get(ID)).setFuzzer(MDC.get("fuzzerKey"));
        if (testCaseMap.get(MDC.get(ID)).isNotSkipped()) {
            testCaseExporter.writeTestCase(testCaseMap.get(MDC.get(ID)));
        }
    }

    public void startSession() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("UTC"));
        LOGGER.start("Starting {}, version {}, build-time {} UTC", ansi().fg(Ansi.Color.GREEN).a(buildProperties.getName().toUpperCase()), ansi().fg(Ansi.Color.GREEN).a(buildProperties.getVersion()), ansi().fg(Ansi.Color.GREEN).a(formatter.format(buildProperties.getTime())).reset());
        LOGGER.note("{}", ansi().fgGreen().a("Processing configuration...").reset());
    }

    public void endSession() {
        testCaseExporter.writeSummary(testCaseMap, executionStatisticsListener);
        testCaseExporter.writeHelperFiles();
        testCaseExporter.writePerformanceReport(testCaseMap);
        testCaseExporter.printExecutionDetails(executionStatisticsListener);
    }

    public void reportWarn(PrettyLogger logger, String message, Object... params) {
        executionStatisticsListener.increaseWarns();
        logger.warning(message, params);
        recordResult(message, params, Level.WARN.toString().toLowerCase());
    }

    public void reportWarn(PrettyLogger logger, CatsResult catsResult, Object... params) {
        this.reportWarn(logger, catsResult.getMessage(), params);
        setResultReason(catsResult);
    }

    private void setResultReason(CatsResult catsResult) {
        CatsTestCase testCase = testCaseMap.get(MDC.get(ID));
        testCase.setResultReason(catsResult.getReason());
    }

    public void reportError(PrettyLogger logger, CatsResult catsResult, Object... params) {
        this.reportError(logger, catsResult.getMessage(), params);
        setResultReason(catsResult);
    }

    public void reportError(PrettyLogger logger, String message, Object... params) {
        executionStatisticsListener.increaseErrors();
        logger.error(message, params);
        this.addRequest(CatsRequest.empty());
        this.addResponse(CatsResponse.empty());
        this.recordResult(message, params, Level.ERROR.toString().toLowerCase());
    }

    private void reportSkipped(PrettyLogger logger, Object... params) {
        executionStatisticsListener.increaseSkipped();
        logger.skip("Skipped due to: {}", params);
        recordResult("Skipped due to: {}", params, "skipped");
    }

    public void reportInfo(PrettyLogger logger, String message, Object... params) {
        executionStatisticsListener.increaseSuccess();
        logger.success(message, params);
        this.recordResult(message, params, "success");
    }

    public void reportInfo(PrettyLogger logger, CatsResult catsResult, Object... params) {
        this.reportInfo(logger, catsResult.getMessage(), params);
    }

    public void reportResult(PrettyLogger logger, FuzzingData data, CatsResponse response, ResponseCodeFamily expectedResultCode) {
        this.reportResult(logger, data, response, expectedResultCode, true);
    }

    public void reportResult(PrettyLogger logger, FuzzingData data, CatsResponse response, ResponseCodeFamily expectedResultCode, boolean shouldMatchToResponseSchema) {
        boolean matchesResponseSchema = !shouldMatchToResponseSchema || this.matchesResponseSchema(response, data);
        boolean responseCodeExpected = this.isResponseCodeExpected(response, expectedResultCode);
        boolean responseCodeDocumented = this.isResponseCodeDocumented(data, response);

        ResponseAssertions assertions = ResponseAssertions.builder().matchesResponseSchema(matchesResponseSchema)
                .responseCodeDocumented(responseCodeDocumented).responseCodeExpected(responseCodeExpected).
                responseCodeUnimplemented(ResponseCodeFamily.isUnimplemented(response.getResponseCode())).build();

        if (assertions.isResponseCodeExpectedAndDocumentedAndMatchesResponseSchema()) {
            this.reportInfo(logger, CatsResult.OK, response.responseCodeAsString());
        } else if (assertions.isResponseCodeExpectedAndDocumentedButDoesntMatchResponseSchema()) {
            this.reportWarn(logger, CatsResult.NOT_MATCHING_RESPONSE_SCHEMA, response.responseCodeAsString());
        } else if (assertions.isResponseCodeExpectedButNotDocumented()) {
            this.reportWarn(logger, CatsResult.UNDOCUMENTED_RESPONSE_CODE, expectedResultCode.allowedResponseCodes(), response.responseCodeAsString(), data.getResponseCodes());
        } else if (assertions.isResponseCodeDocumentedButNotExpected()) {
            this.reportError(logger, CatsResult.UNEXPECTED_RESPONSE_CODE, expectedResultCode.allowedResponseCodes(), response.responseCodeAsString());
        } else if (assertions.isResponseCodeUnimplemented()) {
            this.reportWarn(logger, CatsResult.NOT_IMPLEMENTED);
        } else {
            this.reportError(logger, CatsResult.UNEXPECTED_BEHAVIOUR, expectedResultCode.allowedResponseCodes(), response.responseCodeAsString());
        }
    }

    private boolean isResponseCodeDocumented(FuzzingData data, CatsResponse response) {
        return data.getResponseCodes().contains(response.responseCodeAsString()) || isNotTypicalDocumentedResponseCode(response);
    }

    public void skipTest(PrettyLogger logger, String skipReason) {
        this.addExpectedResult(logger, "Test will be skipped!");
        this.reportSkipped(logger, skipReason);
        this.addRequest(CatsRequest.empty());
        this.addResponse(CatsResponse.empty());
    }

    private void recordResult(String message, Object[] params, String success) {
        CatsTestCase testCase = testCaseMap.get(MDC.get(ID));
        testCase.setResult(success);
        testCase.setResultDetails(replaceBrackets(message, params));
    }

    /**
     * The response code is expected if the response code received from the server matches the Cats test case expectations.
     * There is also a particular case when we fuzz GET requests, and we reach unimplemented endpoints. This is why we also test for 501
     *
     * @param response
     * @param expectedResultCode
     * @return
     */
    private boolean isResponseCodeExpected(CatsResponse response, ResponseCodeFamily expectedResultCode) {
        return expectedResultCode.allowedResponseCodes().contains(String.valueOf(response.responseCodeAsString())) || response.getResponseCode() == 501;
    }

    private boolean matchesResponseSchema(CatsResponse response, FuzzingData data) {
        JsonElement jsonElement = JsonParser.parseString(response.getBody());
        List<String> responses = data.getResponses().get(response.responseCodeAsString());
        return isActualResponseMatchingDocumentedResponses(response, jsonElement, responses)
                || isResponseEmpty(response, responses)
                || isNotTypicalDocumentedResponseCode(response);
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
        if (element.isJsonObject() && PayloadGenerator.GlobalData.getAdditionalProperties().contains(name)) {
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
