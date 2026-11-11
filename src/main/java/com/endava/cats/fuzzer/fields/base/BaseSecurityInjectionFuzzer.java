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
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.RequestTargetResolver;
import com.endava.cats.model.SecurityFinding;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Base class for security injection fuzzers (SQL injection, XSS, Command injection, etc.).
 * <p>
 * This fuzzer sends injection payloads to string fields and analyzes the response
 * for indicators of successful injection or information disclosure.
 * </p>
 * <p>
 * Reflections of the payload are stripped from the response before looking for evidence. Candidate findings are
 * then verified by {@link InjectionEvidenceVerifier} against a baseline response and a replay, and reported with a
 * {@link FindingConfidence}: reliable findings as errors, tentative findings as warnings (errors on 5XX responses).
 * </p>
 * <p>
 * By default, only a curated top 10 payloads are used per injection type to reduce execution time.
 * Use the {@code --includeAllInjectionPayloads} flag to enable the full payload set.
 * </p>
 */
public abstract class BaseSecurityInjectionFuzzer implements Fuzzer {
    private static final String ROOT_FIELD = "$";
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;
    private final SecurityFuzzerArguments securityFuzzerArguments;
    private final InjectionEvidenceVerifier evidenceVerifier;

    /**
     * Creates a new BaseSecurityInjectionFuzzer instance.
     *
     * @param simpleExecutor          the executor used to run the fuzz logic
     * @param testCaseListener        the test case listener for reporting results
     * @param securityFuzzerArguments the security fuzzer arguments
     * @param evidenceVerifier        verifies candidate findings against a baseline response and a replay
     */
    protected BaseSecurityInjectionFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                                          SecurityFuzzerArguments securityFuzzerArguments,
                                          InjectionEvidenceVerifier evidenceVerifier) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
        this.securityFuzzerArguments = securityFuzzerArguments;
        this.evidenceVerifier = evidenceVerifier;
    }

    @Override
    public boolean isApplicableTo(FuzzingData data) {
        return data.isRootTextPayload() || Fuzzer.super.isApplicableTo(data);
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
        Set<String> stringFields = new HashSet<>(FuzzingData.filterFieldsInPayload(data, field -> {
            var schema = data.getRequestPropertyTypes().get(field);
            return CatsModelUtils.isStringSchema(schema) && shouldFuzzField(field);
        }, 0));
        if (data.isRootTextPayload()) {
            stringFields.add(ROOT_FIELD);
        }
        return stringFields;
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

    /**
     * Whether a reflection of the payload is itself the evidence reported by this fuzzer (e.g. stored XSS).
     * When true, reflections are not stripped and findings are not verified, so they stay tentative.
     *
     * @return false by default
     */
    protected boolean isReflectionTheEvidence() {
        return false;
    }

    private void fuzzField(FuzzingData data, String field) {
        getPayloadsToUse().forEach(payload -> sendPayload(new InjectionAttempt(data, field, payload)));
    }

    private void sendPayload(InjectionAttempt attempt) {
        simpleExecutor.execute(requestContext(attempt, (response, data) -> processResponse(response, data, attempt)));
    }

    private SimpleExecutorContext requestContext(InjectionAttempt attempt, BiConsumer<CatsResponse, FuzzingData> responseProcessor) {
        return SimpleExecutorContext.builder()
                .responseProcessor(responseProcessor)
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .fuzzingData(attempt.data())
                .logger(logger)
                .scenario("Send %s injection payload in field [%s]: [%s]"
                        .formatted(getInjectionType(), attempt.field(), truncatePayload(attempt.payload())))
                .fuzzer(this)
                .payload(fuzzedPayload(attempt))
                .mutationTargets(mutationTargets(attempt))
                .build();
    }

    private static String fuzzedPayload(InjectionAttempt attempt) {
        return attempt.isRootField() ? JsonUtils.serialize(attempt.payload())
                : CatsUtil.justReplaceField(attempt.data().getPayload(), attempt.field(), attempt.payload()).json();
    }

    private static List<RequestTarget> mutationTargets(InjectionAttempt attempt) {
        return attempt.isRootField() ? List.of(RequestTarget.requestBody())
                : RequestTargetResolver.resolvePayloadField(attempt.data(), attempt.field());
    }

    private List<String> getPayloadsToUse() {
        if (securityFuzzerArguments.isIncludeAllInjectionPayloads()) {
            return getAllInjectionPayloads();
        }
        return getTopInjectionPayloads();
    }

    private void processResponse(CatsResponse response, FuzzingData data, InjectionAttempt attempt) {
        InjectionDetectionResult detection = detect(response, data, attempt.payload());
        if (!detection.isVulnerable()) {
            reportWithoutFinding(response, data);
            return;
        }
        verifyFinding(data, attempt, detection).ifPresentOrElse(
                confidence -> testCaseListener.reportSecurityFinding(logger, data, response,
                        new SecurityFinding(detection.getTitle(), detection.getMessage(), confidence)),
                () -> reportWithoutFinding(response, data));
    }

    private InjectionDetectionResult detect(CatsResponse response, FuzzingData data, String payload) {
        CatsResponse evidence = isReflectionTheEvidence() ? response : PayloadReflections.strip(response, payload);
        return detectInjectionEvidence(evidence, data);
    }

    private Optional<FindingConfidence> verifyFinding(FuzzingData data, InjectionAttempt attempt,
                                                      InjectionDetectionResult detection) {
        if (isReflectionTheEvidence()) {
            return Optional.of(FindingConfidence.TENTATIVE);
        }
        return evidenceVerifier.verify(new InjectionEvidenceVerifier.Check(data, detection.isStrong(),
                candidate -> detect(candidate, data, attempt.payload()).isVulnerable(),
                new InjectionEvidenceVerifier.Probes(() -> simpleExecutor.replayOriginal(data),
                        () -> simpleExecutor.replay(requestContext(attempt, null)))));
    }

    private void reportWithoutFinding(CatsResponse response, FuzzingData data) {
        int responseCode = response.getResponseCode();
        if (ResponseCodeFamily.is5xxCode(responseCode)) {
            reportServerError(data, responseCode);
        } else if (ResponseCodeFamily.is4xxCode(responseCode)) {
            testCaseListener.reportResultInfo(logger, data, "Injection payload rejected",
                    "Server properly rejected the %s injection payload with response code %d"
                            .formatted(getInjectionType(), responseCode));
        } else if (ResponseCodeFamily.is2xxCode(responseCode)) {
            reportAccepted(data, responseCode);
        } else {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.UNEXPECTED_RESPONSE_CODE.value(),
                    "Received unexpected response code %d for %s injection payload"
                            .formatted(responseCode, getInjectionType()));
        }
    }

    private void reportServerError(FuzzingData data, int responseCode) {
        testCaseListener.reportResultError(
                logger, data, CatsResultFactory.Reason.SERVER_ERROR.value(),
                "Server returned %d error when processing %s injection payload. This may indicate a vulnerability or improper error handling."
                        .formatted(responseCode, getInjectionType()));
    }

    private void reportAccepted(FuzzingData data, int responseCode) {
        if ("XSS".equals(getInjectionType()) && data.getMethod() != HttpMethod.GET) {
            testCaseListener.reportResultWarn(
                    logger, data, "XSS payload accepted without validation",
                    "API accepted XSS payload (response code %d). For APIs, implement input validation to reject dangerous characters (e.g., <, >, script tags) for constrained fields. APIs should store raw data, but validate to prevent abuse and unsafe usage in secondary contexts (emails, PDFs, logs, admin UIs)."
                            .formatted(responseCode));
            return;
        }
        testCaseListener.reportResultInfo(
                logger, data, "Injection payload accepted",
                "%s injection payload was accepted (response code %d). Verify that proper input validation and output encoding are in place."
                        .formatted(getInjectionType(), responseCode));
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
     * beyond simple error pattern matching. The response body received here has
     * reflections of the payload already stripped, unless {@link #isReflectionTheEvidence()} is true.
     *
     * @param response the HTTP response to analyze
     * @param data     the fuzzing data context
     * @return detection result indicating if vulnerability was found
     */
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data) {
        return InjectionDetectionResult.notVulnerable();
    }

    private record InjectionAttempt(FuzzingData data, String field, String payload) {
        boolean isRootField() {
            return data.isRootTextPayload() && ROOT_FIELD.equals(field);
        }
    }

    /**
     * Result of injection detection analysis.
     */
    @Getter
    protected static class InjectionDetectionResult {
        private final boolean vulnerable;
        private final boolean strong;
        private final String title;
        private final String message;

        private InjectionDetectionResult(boolean vulnerable, boolean strong, String title, String message) {
            this.vulnerable = vulnerable;
            this.strong = strong;
            this.title = title;
            this.message = message;
        }

        public static InjectionDetectionResult notVulnerable() {
            return new InjectionDetectionResult(false, false, null, null);
        }

        /**
         * Evidence that a plain echo of the payload cannot produce, such as file contents, command output,
         * the computed result of an injected expression, or query output.
         *
         * @param title   the finding title, used as result reason
         * @param message the finding details
         * @return a vulnerable result backed by strong evidence
         */
        public static InjectionDetectionResult strongEvidence(String title, String message) {
            return new InjectionDetectionResult(true, true, title, message);
        }

        /**
         * Heuristic evidence, such as error keywords, scores or single indicators.
         *
         * @param title   the finding title, used as result reason
         * @param message the finding details
         * @return a vulnerable result backed by weak evidence
         */
        public static InjectionDetectionResult weakEvidence(String title, String message) {
            return new InjectionDetectionResult(true, false, title, message);
        }
    }
}
