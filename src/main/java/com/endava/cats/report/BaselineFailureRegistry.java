package com.endava.cats.report;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.ErrorSignature;
import com.endava.cats.model.RequestVocabulary;
import com.endava.cats.report.error.extract.EmptyBody;
import com.endava.cats.report.error.mask.ErrorSignatureMasker;
import com.endava.cats.util.ConsoleUtils;
import com.google.gson.JsonElement;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tracks baseline seed-wave failures ({@code HappyPath}, {@code OnlyRequiredFields}) per operation during a run
 * and annotates subsequent fuzzed tests that fail on the same operation with the same HTTP status, result reason and
 * online error signature key. The result reason is part of the key because successful responses that echo the created
 * resource have near-identical signatures whatever the finding is.
 */
@Singleton
public class BaselineFailureRegistry {
    private static final Set<String> SEED_FUZZERS = Set.of("HappyPath", "OnlyRequiredFields");

    private final Map<BaselineKey, BaselineOrigin> failures = new LinkedHashMap<>();

    /**
     * Clears all recorded baseline failures at the start of a new session.
     */
    public void clear() {
        failures.clear();
    }

    /**
     * Records a seed-fuzzer failure or annotates a non-seed error/warning test case when its online signature
     * and HTTP status match a recorded baseline failure for the same operation.
     *
     * @param testCase the completed test case
     */
    public void recordOrAnnotate(CatsTestCase testCase) {
        if (testCase == null || !isErrorOrWarning(testCase.getResult())) {
            return;
        }
        String fuzzer = normalizeFuzzer(testCase.getFuzzer());
        if (fuzzer.isEmpty()) {
            return;
        }
        computeKey(testCase).ifPresent(key -> applyKey(testCase, fuzzer, key));
    }

    private void applyKey(CatsTestCase testCase, String fuzzer, BaselineKey key) {
        if (isSeedFuzzer(fuzzer)) {
            if (StringUtils.isNotBlank(testCase.getTestId())) {
                failures.putIfAbsent(key, new BaselineOrigin(testCase.getTestId(), fuzzer));
            }
            return;
        }
        Optional.ofNullable(failures.get(key)).ifPresent(origin -> {
            testCase.setSameErrorAsBaselineTest(origin.testId());
            testCase.setSameErrorAsBaselineFuzzer(origin.fuzzer());
        });
    }

    private static Optional<BaselineKey> computeKey(CatsTestCase testCase) {
        String operation = resolveOperation(testCase);
        String body = extractResponseBody(testCase.getResponse());
        if (operation.isEmpty() || EmptyBody.isEmpty(body)) {
            return Optional.empty();
        }
        CatsRequest request = Optional.ofNullable(testCase.getRequest()).orElse(CatsRequest.empty());
        String contractPath = StringUtils.defaultIfBlank(testCase.getContractPath(), testCase.getPath());
        String url = StringUtils.defaultIfBlank(request.getUrl(), testCase.getFullRequestPath());
        ErrorSignature signature = ErrorSignatureMasker.signatureOf(
                body, null, RequestVocabulary.from(request.getPayload(), url, contractPath));
        if (isEmptySignature(signature)) {
            return Optional.empty();
        }
        return Optional.of(new BaselineKey(operation, testCase.getResponse().getResponseCode(),
                StringUtils.defaultString(testCase.getResultReason()), signatureHash(signature)));
    }

    private static boolean isEmptySignature(ErrorSignature signature) {
        return ErrorSignatureMasker.EMPTY_TYPE.equals(signature.errorType())
                || (signature.errorType().isEmpty() && signature.tokens().isEmpty() && signature.stackFrame().isEmpty());
    }

    static String signatureHash(ErrorSignature signature) {
        String raw = signature.errorType() + "|" + String.join(" ", signature.tokens()) + "|" + signature.stackFrame();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String resolveOperation(CatsTestCase testCase) {
        String method = Optional.ofNullable(testCase.getRequest())
                .map(CatsRequest::getHttpMethod)
                .orElse("")
                .trim()
                .toUpperCase(Locale.ROOT);
        String path = StringUtils.defaultIfBlank(testCase.getContractPath(), testCase.getPath());
        if (method.isEmpty() || "####".equals(method) || StringUtils.isBlank(path) || "####".equals(path)) {
            return "";
        }
        return method + " " + path.trim();
    }

    private static String extractResponseBody(CatsResponse response) {
        if (response == null) {
            return "";
        }
        if (StringUtils.isNotBlank(response.getBody())) {
            return response.getBody();
        }
        JsonElement jsonBody = response.getJsonBody();
        if (jsonBody == null || jsonBody.isJsonNull()) {
            return "";
        }
        return jsonBody.isJsonPrimitive() ? jsonBody.getAsString() : jsonBody.toString();
    }

    private static boolean isErrorOrWarning(String result) {
        return "error".equalsIgnoreCase(result)
                || CatsResultFactory.WARN.equalsIgnoreCase(result)
                || "warning".equalsIgnoreCase(result);
    }

    private static String normalizeFuzzer(String fuzzer) {
        if (StringUtils.isBlank(fuzzer)) {
            return "";
        }
        return ConsoleUtils.removeTrimSanitize(ConsoleUtils.sanitizeFuzzerName(fuzzer.trim()));
    }

    private static boolean isSeedFuzzer(String normalizedFuzzer) {
        return SEED_FUZZERS.stream().anyMatch(seed -> seed.equalsIgnoreCase(normalizedFuzzer));
    }

    /**
     * Checks whether the given test case was produced by a seed-wave fuzzer ({@code HappyPath} or {@code OnlyRequiredFields}).
     *
     * @param testCase the test case to inspect
     * @return {@code true} if the test case is a seed test
     */
    public static boolean isSeedTestCase(CatsTestCase testCase) {
        return testCase != null && isSeedFuzzer(normalizeFuzzer(testCase.getFuzzer()));
    }

    record BaselineKey(String operation, int statusCode, String resultReason, String signatureKey) {
    }

    record BaselineOrigin(String testId, String fuzzer) {
    }
}
