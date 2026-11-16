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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Base class for security injection fuzzers (SQL injection, XSS, Command injection, etc.).
 * <p>
 * Sends injection payloads to string fields and analyzes responses using both reflection-stripped content evidence
 * and non-text behavioral oracles (time-based blind linear regression, computed arithmetic canaries, and boolean
 * differential pairs). Candidate findings are verified against a fresh baseline and replay, and reported with a
 * {@link FindingConfidence}.
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
        OperationBaseline baseline = new OperationBaseline(
                () -> simpleExecutor.replayOriginal(data),
                evidenceVerifier.effectiveBaselinePayload(data));
        for (String field : stringFields) {
            fuzzField(data, field, baseline);
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
     *
     * @param fieldName the name of the field
     * @return true if the field should be fuzzed, false otherwise
     */
    protected boolean shouldFuzzField(String fieldName) {
        return true;
    }

    /**
     * Whether a reflection of the payload is itself the evidence reported by this fuzzer (e.g. stored XSS).
     *
     * @return false by default
     */
    protected boolean isReflectionTheEvidence() {
        return false;
    }

    private void fuzzField(FuzzingData data, String field, OperationBaseline baseline) {
        getPayloadsToUse().forEach(payload -> sendPayload(new InjectionAttempt(data, field, payload), baseline));
        fuzzBooleanPairs(data, field, baseline);
    }

    private void sendPayload(InjectionAttempt attempt, OperationBaseline baseline) {
        simpleExecutor.execute(requestContext(attempt, (response, data) -> processResponse(response, data, attempt, baseline)));
    }

    private void fuzzBooleanPairs(FuzzingData data, String field, OperationBaseline baseline) {
        List<BooleanInjectionPair> pairs = getBooleanPairsToUse();
        if (pairs.isEmpty() || data.getMethod() == HttpMethod.DELETE) {
            return;
        }
        Optional<String> originalValue = extractOriginalFieldValue(data, field, baseline.effectivePayload());
        if (originalValue.isEmpty()) {
            return;
        }
        AtomicBoolean fieldConfirmed = new AtomicBoolean(false);
        for (BooleanInjectionPair pair : pairs) {
            if (fieldConfirmed.get()) {
                break;
            }
            sendBooleanPair(new BooleanPairAttempt(data, field, originalValue.get(), pair),
                    new BooleanFieldState(baseline, fieldConfirmed));
        }
    }

    private void sendBooleanPair(BooleanPairAttempt attempt, BooleanFieldState state) {
        String divergentValue = attempt.pair().divergentPayload().apply(attempt.originalValue());
        InjectionAttempt primary = new InjectionAttempt(attempt.data(), attempt.field(), divergentValue);
        String scenario = "Send %s boolean differential probe (%s) in field [%s]: [%s]"
                .formatted(getInjectionType(), attempt.pair().label(), attempt.field(), truncatePayload(divergentValue));
        simpleExecutor.execute(requestContextWithScenario(primary, scenario,
                (response, data) -> processBooleanPairResponse(response, data, attempt, state)));
    }

    private void processBooleanPairResponse(CatsResponse response, FuzzingData data,
                                            BooleanPairAttempt attempt, BooleanFieldState state) {
        String neutralValue = attempt.pair().neutralPayload().apply(attempt.originalValue());
        String divergentValue = attempt.pair().divergentPayload().apply(attempt.originalValue());
        BooleanDifferentialOracle.DifferentialCheck check = new BooleanDifferentialOracle.DifferentialCheck(
                attempt.pair(),
                new BooleanDifferentialOracle.ProbeValues(attempt.originalValue(), neutralValue, divergentValue),
                new BooleanDifferentialOracle.ProbeTransport(
                        state.baseline()::firstBaseline,
                        () -> simpleExecutor.replayOriginal(data),
                        val -> simpleExecutor.replay(requestContext(new InjectionAttempt(data, attempt.field(), val), null))),
                new BooleanDifferentialOracle.FindingMeta(getConfirmableFindingReason(), getInjectionType()));
        BooleanDifferentialOracle.verify(response, check).ifPresentOrElse(
                finding -> {
                    state.fieldConfirmed().set(true);
                    testCaseListener.reportSecurityFinding(logger, data, response, finding);
                },
                () -> reportWithoutFinding(response, data));
    }

    private static Optional<String> extractOriginalFieldValue(FuzzingData data, String field, String effectivePayload) {
        if (data.isRootTextPayload() && ROOT_FIELD.equals(field)) {
            return Optional.of(deserializeRootText(effectivePayload));
        }
        Object raw = JsonUtils.getVariableFromJson(effectivePayload, field);
        if (raw == null || JsonUtils.isNotSet(String.valueOf(raw))) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(raw));
    }

    private static String deserializeRootText(String payload) {
        if (payload == null) {
            return "";
        }
        try {
            String parsed = JsonUtils.JSON_STRICT_PARSER.fromJson(payload, String.class);
            return parsed == null ? payload : parsed;
        } catch (RuntimeException _) {
            return payload;
        }
    }

    private SimpleExecutorContext requestContext(InjectionAttempt attempt, BiConsumer<CatsResponse, FuzzingData> responseProcessor) {
        String scenario = "Send %s injection payload in field [%s]: [%s]"
                .formatted(getInjectionType(), attempt.field(), truncatePayload(attempt.payload()));
        return requestContextWithScenario(attempt, scenario, responseProcessor);
    }

    private SimpleExecutorContext requestContextWithScenario(InjectionAttempt attempt, String scenario,
                                                             BiConsumer<CatsResponse, FuzzingData> responseProcessor) {
        return SimpleExecutorContext.builder()
                .responseProcessor(responseProcessor)
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .fuzzingData(attempt.data())
                .logger(logger)
                .scenario(scenario)
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
        return securityFuzzerArguments.isIncludeAllInjectionPayloads()
                ? getAllInjectionPayloads()
                : getTopInjectionPayloads();
    }

    private List<BooleanInjectionPair> getBooleanPairsToUse() {
        return securityFuzzerArguments.isIncludeAllInjectionPayloads()
                ? getAllBooleanInjectionPairs()
                : getTopBooleanInjectionPairs();
    }

    private void processResponse(CatsResponse response, FuzzingData data, InjectionAttempt attempt, OperationBaseline baseline) {
        Optional<SecurityFinding> timingFinding = verifyTimingOracle(response, attempt, baseline);
        if (timingFinding.isPresent()) {
            testCaseListener.reportSecurityFinding(logger, data, response, timingFinding.get());
            return;
        }
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

    private Optional<SecurityFinding> verifyTimingOracle(CatsResponse response, InjectionAttempt attempt,
                                                         OperationBaseline baseline) {
        return TimingInjectionOracle.parseTimingSpec(attempt.payload())
                .flatMap(spec -> TimingInjectionOracle.verify(response, spec, new TimingInjectionOracle.TimingContext(
                        getConfirmableFindingReason(),
                        getInjectionType(),
                        baseline::baselineTimeMs,
                        seconds -> replayPayload(attempt, spec.payloadBuilder().apply(seconds)))));
    }

    private CatsResponse replayPayload(InjectionAttempt baseAttempt, String altPayload) {
        InjectionAttempt altAttempt = new InjectionAttempt(baseAttempt.data(), baseAttempt.field(), altPayload);
        return simpleExecutor.replay(requestContext(altAttempt, null));
    }

    private InjectionDetectionResult detect(CatsResponse response, FuzzingData data, String payload) {
        CatsResponse evidence = isReflectionTheEvidence() ? response : PayloadReflections.strip(response, payload);
        return detectInjectionEvidence(evidence, data, payload);
    }

    private Optional<FindingConfidence> verifyFinding(FuzzingData data, InjectionAttempt attempt,
                                                      InjectionDetectionResult detection) {
        if (isReflectionTheEvidence()) {
            return Optional.of(FindingConfidence.TENTATIVE);
        }
        InjectionEvidenceVerifier.Check check = new InjectionEvidenceVerifier.Check(data, detection.isStrong(),
                candidate -> detect(candidate, data, attempt.payload()).isVulnerable(),
                new InjectionEvidenceVerifier.Probes(() -> simpleExecutor.replayOriginal(data),
                        () -> simpleExecutor.replay(requestContext(attempt, null))));
        return Optional.ofNullable(detection.getCanaryReplay())
                .map(canary -> evidenceVerifier.verifyCustomReplay(check,
                        () -> canary.detector().test(replayPayload(attempt, canary.confirmationPayload()))))
                .orElseGet(() -> evidenceVerifier.verify(check));
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

    protected abstract String getInjectionType();

    protected abstract List<String> getTopInjectionPayloads();

    protected abstract List<String> getAllInjectionPayloads();

    /**
     * Canonical {@link CatsResultFactory.Reason} title used when a non-text oracle confirms a finding.
     */
    protected String getConfirmableFindingReason() {
        return "%s injection vulnerability detected".formatted(getInjectionType());
    }

    protected List<BooleanInjectionPair> getTopBooleanInjectionPairs() {
        return List.of();
    }

    protected List<BooleanInjectionPair> getAllBooleanInjectionPairs() {
        return getTopBooleanInjectionPairs();
    }

    // The payload is consumed by payload-aware overrides; keep this hook forwarding to the two-argument detector.
    @SuppressWarnings("java:S1172")
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data, String ignoredPayload) {
        return detectInjectionEvidence(response, data);
    }

    // Subclasses override this hook to inspect the response and request; the default remains a no-op.
    @SuppressWarnings("java:S1172")
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data) {
        return InjectionDetectionResult.notVulnerable();
    }

    private record InjectionAttempt(FuzzingData data, String field, String payload) {
        boolean isRootField() {
            return data.isRootTextPayload() && ROOT_FIELD.equals(field);
        }
    }

    private record BooleanPairAttempt(FuzzingData data, String field, String originalValue, BooleanInjectionPair pair) {
    }

    private record BooleanFieldState(OperationBaseline baseline, AtomicBoolean fieldConfirmed) {
    }

    private static final class OperationBaseline {
        private final Supplier<CatsResponse> sender;
        private final String effectivePayload;
        private CatsResponse cachedFresh;
        private boolean fetched;

        OperationBaseline(Supplier<CatsResponse> sender, String effectivePayload) {
            this.sender = sender;
            this.effectivePayload = effectivePayload;
        }

        CatsResponse firstBaseline() {
            if (!fetched) {
                fetched = true;
                cachedFresh = sender.get();
            }
            return cachedFresh;
        }

        long baselineTimeMs() {
            CatsResponse response = firstBaseline();
            return TimingInjectionOracle.isHttpResponse(response) ? response.getResponseTimeInMs() : -1L;
        }

        String effectivePayload() {
            return effectivePayload;
        }
    }

    /**
     * Secondary confirmation probe used for computed arithmetic canaries.
     */
    public record CanaryReplay(String confirmationPayload, Predicate<CatsResponse> detector) {
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
        private final CanaryReplay canaryReplay;

        private InjectionDetectionResult(boolean vulnerable, boolean strong, String title,
                                         String message, CanaryReplay canaryReplay) {
            this.vulnerable = vulnerable;
            this.strong = strong;
            this.title = title;
            this.message = message;
            this.canaryReplay = canaryReplay;
        }

        public static InjectionDetectionResult notVulnerable() {
            return new InjectionDetectionResult(false, false, null, null, null);
        }

        public static InjectionDetectionResult strongEvidence(String title, String message) {
            return new InjectionDetectionResult(true, true, title, message, null);
        }

        public static InjectionDetectionResult computedCanaryEvidence(String title, String message, CanaryReplay canaryReplay) {
            return new InjectionDetectionResult(true, true, title, message, canaryReplay);
        }

        public static InjectionDetectionResult weakEvidence(String title, String message) {
            return new InjectionDetectionResult(true, false, title, message, null);
        }
    }
}
