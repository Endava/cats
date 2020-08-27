package com.endava.cats.report;

import com.endava.cats.CatsMain;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.report.CatsTestCase;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.Ansi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * This class exposes methods to record the progress of a test case
 */
@Component
public class TestCaseListener {

    protected static final String ID = "id";
    private static final Logger LOGGER = LoggerFactory.getLogger(TestCaseListener.class);
    private static final String SEPARATOR = StringUtils.repeat("-", 150);
    protected final Map<String, CatsTestCase> testCaseMap = new HashMap<>();
    private final ExecutionStatisticsListener executionStatisticsListener;
    private final TestCaseExporter testCaseExporter;
    private final BuildProperties buildProperties;
    private long t0;


    @Autowired
    public TestCaseListener(ExecutionStatisticsListener er, TestCaseExporter tce, BuildProperties buildProperties) {
        this.executionStatisticsListener = er;
        this.testCaseExporter = tce;
        this.buildProperties = buildProperties;
    }

    private static String replaceBrackets(String message, Object... params) {
        for (Object obj : params) {
            message = message.replaceFirst("\\{}", String.valueOf(obj));
        }

        return message;
    }

    public void createAndExecuteTest(Logger externalLogger, Fuzzer fuzzer, Runnable s) {
        MDC.put(ID, "Test " + CatsMain.TEST.incrementAndGet());
        this.startTestCase();
        try {
            s.run();
        } catch (Exception e) {
            this.reportError(externalLogger, "Fuzzer [{}] failed due to [{}]", fuzzer.getClass().getSimpleName(), e.getMessage());
        }
        this.endTestCase();
        LOGGER.info("{} {}", SEPARATOR, "\n");
        MDC.put(ID, "");
    }

    private void startTestCase() {
        testCaseMap.put(MDC.get(ID), new CatsTestCase());
    }

    public void addScenario(Logger logger, String scenario, Object... params) {
        logger.info(scenario, params);
        testCaseMap.get(MDC.get(ID)).setScenario(replaceBrackets(scenario, params));
    }

    public void addExpectedResult(Logger logger, String expectedResult, Object... params) {
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
        testCaseMap.get(MDC.get(ID)).setFuzzer(MDC.get("fuzzer"));
        if (testCaseMap.get(MDC.get(ID)).isNotSkipped()) {
            testCaseExporter.writeToFile(testCaseMap.get(MDC.get(ID)));
        }
    }

    @PostConstruct
    public void startSession() {
        t0 = System.currentTimeMillis();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("UTC"));
        LOGGER.info("Starting {}, version {}, build-time {} UTC", ansi().fg(Ansi.Color.GREEN).a(buildProperties.getName().toUpperCase()), ansi().fg(Ansi.Color.GREEN).a(buildProperties.getVersion()), ansi().fg(Ansi.Color.GREEN).a(formatter.format(buildProperties.getTime())));
        ansi().reset();
    }

    @PreDestroy
    public void endSession() {
        testCaseExporter.writeSummary(testCaseMap, executionStatisticsListener.getAll(), executionStatisticsListener.getSuccess(), executionStatisticsListener.getWarns(), executionStatisticsListener.getErrors());
        testCaseExporter.writeReportFiles();
        LOGGER.info("CATS finished in {} ms. Total (excluding skipped) requests {}. Passed: {}, warnings: {}, errors: {}, skipped {}. You can check the test_cases folder for more details about the payloads.",
                (System.currentTimeMillis() - t0), executionStatisticsListener.getAll(), executionStatisticsListener.getSuccess(), executionStatisticsListener.getWarns(), executionStatisticsListener.getErrors(), executionStatisticsListener.getSkipped());
    }

    public void reportWarn(Logger logger, String message, Object... params) {
        executionStatisticsListener.increaseWarns();
        logger.warn(message, params);
        recordResult(message, params, Level.WARN.toString().toLowerCase());
    }

    public void reportError(Logger logger, String message, Object... params) {
        executionStatisticsListener.increaseErrors();
        logger.error(message, params);
        this.addRequest(CatsRequest.empty());
        this.addResponse(CatsResponse.empty());
        this.recordResult(message, params, Level.ERROR.toString().toLowerCase());
    }

    private void reportSkipped(Logger logger, Object... params) {
        executionStatisticsListener.increaseSkipped();
        logger.info("Skipped due to: {}", params);
        recordResult("Skipped due to: {}", params, "skipped");
    }

    public void reportInfo(Logger logger, String message, Object... params) {
        executionStatisticsListener.increaseSuccess();
        logger.info(message, params);
        recordResult(message, params, "success");
    }

    public void reportResult(Logger logger, FuzzingData data, CatsResponse response, ResponseCodeFamily expectedResultCode) {
        boolean matchesResponseSchema = this.matchesResponseSchema(response, data);
        boolean responseCodeExpected = this.isResponseCodeExpected(response, expectedResultCode);
        boolean responseCodeDocumented = data.getResponseCodes().contains(response.responseCodeAsString());

        ResponseAssertions assertions = ResponseAssertions.builder().matchesResponseSchema(matchesResponseSchema)
                .responseCodeDocumented(responseCodeDocumented).responseCodeExpected(responseCodeExpected).
                        responseCodeUnimplemented(ResponseCodeFamily.isUnimplemented(response.getResponseCode())).build();

        if (assertions.isResponseCodeExpectedAndDocumentedAndMatchesResponseSchema()) {
            this.reportInfo(logger, "Call returned as expected. Response code {} matches the contract. Response body matches the contract!", response.responseCodeAsString());
        } else if (assertions.isResponseCodeExpectedAndDocumentedButDoesntMatchResponseSchema()) {
            this.reportWarn(logger, "Call returned as expected. Response code {} matches the contract. Response body does NOT match the contract!", response.responseCodeAsString());
        } else if (assertions.isResponseCodeExpectedButNotDocumented()) {
            this.reportWarn(logger, "Call returned as expected, but with undocumented code: expected [{}], actual [{}]. Documented response codes: {}", expectedResultCode.asString(), response.responseCodeAsString(), data.getResponseCodes());
        } else if (assertions.isResponseCodeDocumentedButNotExpected()) {
            this.reportError(logger, "Call returned an unexpected result, but with documented code: expected [{}], actual [{}]", expectedResultCode.asString(), response.responseCodeAsString());
        } else if (assertions.isResponseCodeUnimplemented()) {
            this.reportWarn(logger, "Call returned http code 501: you forgot to implement this functionality!");
        } else {
            this.reportError(logger, "Unexpected behaviour: expected {}, actual [{}]", expectedResultCode.asString(), response.responseCodeAsString());
        }
    }

    public void skipTest(Logger logger, String skipReason) {
        this.addExpectedResult(logger, "Expected result: test will be skipped!");
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
     * The response code is expected if the the response code received from the server matches the Cats test case expectations.
     * There is also a particular case when we fuzz GET requests and we reach unimplemented endpoints. This is why we also test for 501
     *
     * @param response
     * @param expectedResultCode
     * @return
     */
    private boolean isResponseCodeExpected(CatsResponse response, ResponseCodeFamily expectedResultCode) {
        return String.valueOf(response.responseCodeAsString()).startsWith(expectedResultCode.getStartingDigit()) || response.getResponseCode() == 501;
    }

    private boolean matchesResponseSchema(CatsResponse response, FuzzingData data) {
        JsonElement jsonElement = JsonParser.parseString(response.getBody());
        List<String> responses = data.getResponses().get(response.responseCodeAsString());
        return (responses != null && responses.stream().anyMatch(responseSchema -> matchesElement(responseSchema, jsonElement, "ROOT")))
                || ((responses == null || responses.isEmpty()) && isEmptyResponse(response.getBody()));
    }

    private boolean isEmptyResponse(String body) {
        return body.trim().isEmpty() || body.trim().equalsIgnoreCase("[]");
    }

    private boolean matchesElement(String responseSchema, JsonElement element, String name) {
        if (element.isJsonArray()) {
            return matchesArrayElement(responseSchema, element, name);
        }

        return matchesSingleElement(responseSchema, element, name);
    }

    private boolean matchesArrayElement(String responseSchema, JsonElement element, String name) {
        JsonArray jsonArray = ((JsonArray) element);

        if (jsonArray.size() == 0 && JsonParser.parseString(responseSchema).isJsonArray()) {
            return true;
        } else if (jsonArray.size() == 0) {
            return false;
        }

        JsonElement firstElement = jsonArray.get(0);
        return matchesSingleElement(responseSchema, firstElement, name);
    }

    private boolean matchesSingleElement(String responseSchema, JsonElement element, String name) {
        boolean result = true;
        if (element.isJsonObject()) {
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
