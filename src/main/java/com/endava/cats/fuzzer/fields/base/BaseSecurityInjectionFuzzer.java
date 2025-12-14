package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.List;
import java.util.Locale;
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
                .collect(Collectors.toSet());
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
        String responseBody = response.getBody() != null ? response.getBody().toLowerCase(Locale.ROOT) : "";
        int responseCode = response.getResponseCode();

        for (String errorPattern : getErrorPatterns()) {
            if (responseBody.contains(errorPattern.toLowerCase(Locale.ROOT))) {
                testCaseListener.reportResultError(
                        logger, data, "Potential %s vulnerability".formatted(getInjectionType()),
                        "Response contains error pattern [%s] indicating potential %s vulnerability. Response code: %d"
                                .formatted(errorPattern, getInjectionType(), responseCode));
                return;
            }
        }

        if (shouldCheckForPayloadReflection()) {
            for (String payload : getPayloadsToUse()) {
                if (responseBody.contains(payload.toLowerCase(Locale.ROOT))) {
                    testCaseListener.reportResultError(
                            logger, data, "Payload reflected in response",
                            "Injection payload was reflected in the response body, indicating potential %s vulnerability. Response code: %d"
                                    .formatted(getInjectionType(), responseCode));
                    return;
                }
            }
        }

        if (ResponseCodeFamily.is5xxCode(responseCode)) {
            testCaseListener.reportResultError(
                    logger, data, "Server error with injection payload",
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
            testCaseListener.reportResultInfo(
                    logger, data, "Injection payload accepted",
                    "%s injection payload was accepted (response code %d). Payload may contain valid characters that don't require rejection."
                            .formatted(getInjectionType(), responseCode));
            return;
        }

        testCaseListener.reportResultError(
                logger, data, "Unexpected response code",
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
     * Returns patterns that indicate a successful injection or information disclosure.
     * These patterns are searched in the response body (case-insensitive).
     *
     * @return list of error patterns to detect
     */
    protected abstract List<String> getErrorPatterns();

    /**
     * Whether to check if the payload is reflected in the response.
     * This is particularly important for XSS detection.
     *
     * @return true if payload reflection should be checked
     */
    protected boolean shouldCheckForPayloadReflection() {
        return false;
    }
}
