package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for security injection fuzzers (SQL injection, XSS, Command injection, etc.).
 * <p>
 * This fuzzer sends injection payloads to string fields and analyzes the response
 * for indicators of successful injection or information disclosure.
 * </p>
 * <p>
 * By default, only a curated top 10 payloads are used per injection type to reduce execution time.
 * Use the {@code --includeAllInjectionPayloads} flag to enable the full payload set.
 * </p>
 */
public abstract class BaseSecurityInjectionFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;
    private final SecurityFuzzerArguments securityFuzzerArguments;

    /**
     * Creates a new BaseSecurityInjectionFuzzer instance.
     *
     * @param simpleExecutor          the executor used to run the fuzz logic
     * @param testCaseListener        the test case listener for reporting results
     * @param securityFuzzerArguments the security fuzzer arguments
     */
    protected BaseSecurityInjectionFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                                          SecurityFuzzerArguments securityFuzzerArguments) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
        this.securityFuzzerArguments = securityFuzzerArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }

        Set<String> stringFields = getStringFields(data);

        if (stringFields.isEmpty()) {
            logger.skip("No string fields found in the request");
            return;
        }

        logger.info("Found {} string field(s) to test for {} injection", stringFields.size(), getInjectionType());

        for (String field : stringFields) {
            fuzzField(data, field);
        }
    }

    private Set<String> getStringFields(FuzzingData data) {
        return data.getAllFieldsByHttpMethod()
                .stream()
                .filter(field -> JsonUtils.isFieldInJson(data.getPayload(), field))
                .filter(field -> {
                    var schema = data.getRequestPropertyTypes().get(field);
                    return CatsModelUtils.isStringSchema(schema);
                })
                .filter(this::shouldFuzzField)
                .collect(Collectors.toSet());
    }

    /**
     * Determines if a field should be fuzzed based on its name or other characteristics.
     * Default implementation returns true for all fields.
     *
     * @param fieldName the name of the field
     * @return true if the field should be fuzzed, false otherwise
     */
    protected boolean shouldFuzzField(String fieldName) {
        return true;
    }

    private void fuzzField(FuzzingData data, String field) {
        List<String> payloads = getPayloadsToUse();
        for (String payload : payloads) {
            String fuzzedPayload = CatsUtil.justReplaceField(data.getPayload(), field, payload).json();

            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                            .fuzzingData(data)
                            .logger(logger)
                            .scenario("Send %s injection payload in field [%s]: [%s]"
                                    .formatted(getInjectionType(), field, truncatePayload(payload)))
                            .fuzzer(this)
                            .payload(fuzzedPayload)
                            .responseProcessor(this::processResponse)
                            .build()
            );
        }
    }

    private List<String> getPayloadsToUse() {
        if (securityFuzzerArguments.isIncludeAllInjectionPayloads()) {
            return getAllInjectionPayloads();
        }
        return getTopInjectionPayloads();
    }

    private void processResponse(CatsResponse response, FuzzingData data) {
        int responseCode = response.getResponseCode();

        InjectionDetectionResult detectionResult = detectInjectionEvidence(response, data);
        if (detectionResult.isVulnerable()) {
            // XSS reflection is a WARN (validation concern), other injections are ERROR (actual vulnerabilities)
            if ("XSS".equals(getInjectionType())) {
                testCaseListener.reportResultWarn(
                        logger, data, detectionResult.getTitle(),
                        detectionResult.getMessage());
            } else {
                testCaseListener.reportResultError(
                        logger, data, detectionResult.getTitle(),
                        detectionResult.getMessage());
            }
            return;
        }

        if (ResponseCodeFamily.is5xxCode(responseCode)) {
            testCaseListener.reportResultError(
                    logger, data, CatsResultFactory.Reason.SERVER_ERROR.value(),
                    "Server returned %d error when processing %s injection payload. This may indicate a vulnerability or improper error handling."
                            .formatted(responseCode, getInjectionType()));
            return;
        }

        if (ResponseCodeFamily.is4xxCode(responseCode)) {
            testCaseListener.reportResultInfo(
                    logger, data, "Injection payload rejected",
                    "Server properly rejected the %s injection payload with response code %d"
                            .formatted(getInjectionType(), responseCode));
            return;
        }

        if (ResponseCodeFamily.is2xxCode(responseCode)) {
            if ("XSS".equals(getInjectionType())) {
                testCaseListener.reportResultWarn(
                        logger, data, "XSS payload accepted without validation",
                        "API accepted XSS payload (response code %d). For APIs, implement input validation to reject dangerous characters (e.g., <, >, script tags) for constrained fields. APIs should store raw data, but validate to prevent abuse and unsafe usage in secondary contexts (emails, PDFs, logs, admin UIs)."
                                .formatted(responseCode));
            } else {
                testCaseListener.reportResultInfo(
                        logger, data, "Injection payload accepted",
                        "%s injection payload was accepted (response code %d). Verify that proper input validation and output encoding are in place."
                                .formatted(getInjectionType(), responseCode));
            }
            return;
        }

        testCaseListener.reportResultError(
                logger, data, CatsResultFactory.Reason.UNEXPECTED_RESPONSE_CODE.value(),
                "Received unexpected response code %d for %s injection payload"
                        .formatted(responseCode, getInjectionType()));
    }

    private String truncatePayload(String payload) {
        return payload.length() > 50 ? payload.substring(0, 50) + "..." : payload;
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    /**
     * Returns the type of injection (e.g., "SQL", "XSS", "Command").
     *
     * @return the injection type name
     */
    protected abstract String getInjectionType();

    /**
     * Returns the curated top 10 injection payloads for quick testing.
     * These are the most effective payloads selected for broad coverage.
     *
     * @return list of top injection payloads
     */
    protected abstract List<String> getTopInjectionPayloads();

    /**
     * Returns the full list of injection payloads for comprehensive testing.
     * Used when {@code --includeAllInjectionPayloads} flag is enabled.
     *
     * @return list of all injection payloads
     */
    protected abstract List<String> getAllInjectionPayloads();

    /**
     * Detects evidence of successful injection based on response analysis.
     * This method allows each fuzzer to implement specific detection logic
     * beyond simple error pattern matching.
     *
     * @param response the HTTP response to analyze
     * @param data     the fuzzing data context
     * @return detection result indicating if vulnerability was found
     */
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data) {
        return InjectionDetectionResult.notVulnerable();
    }

    /**
     * Result of injection detection analysis.
     */
    @Getter
    protected static class InjectionDetectionResult {
        private final boolean vulnerable;
        private final String title;
        private final String message;

        private InjectionDetectionResult(boolean vulnerable, String title, String message) {
            this.vulnerable = vulnerable;
            this.title = title;
            this.message = message;
        }

        public static InjectionDetectionResult notVulnerable() {
            return new InjectionDetectionResult(false, null, null);
        }

        public static InjectionDetectionResult vulnerable(String title, String message) {
            return new InjectionDetectionResult(true, title, message);
        }

    }
}
