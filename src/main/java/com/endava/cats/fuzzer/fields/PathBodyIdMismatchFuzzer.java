package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fuzzes {@code PUT} and {@code PATCH} requests where the URL path parameter (such as {@code {id}}
 * or {@code {orderId}}) also appears as a top-level identifier field in the JSON request body.
 * Sends a contradicting identifier in the request body while keeping the URL path parameter intact
 * to detect split-brain resource updates, BOLA misrouting, and unhandled ORM primary-key mutation crashes.
 */
@Singleton
@FieldFuzzer
public class PathBodyIdMismatchFuzzer implements Fuzzer {
    static final String UUID_CANARY_PRIMARY = "11111111-1111-1111-1111-111111111111";
    static final String UUID_CANARY_FALLBACK = "22222222-2222-2222-2222-222222222222";
    static final long NUMERIC_CANARY_PRIMARY = 987654L;
    static final long NUMERIC_CANARY_FALLBACK = 987655L;
    static final String STRING_CANARY = "cats-mismatched-body-id";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("^\\d+$");

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(PathBodyIdMismatchFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    record PathBodyIdPair(String pathParam, String bodyField) {
    }

    /**
     * Creates a new PathBodyIdMismatchFuzzer instance.
     *
     * @param simpleExecutor   the executor used to run the fuzz logic
     * @param testCaseListener the test case listener for reporting results
     */
    public PathBodyIdMismatchFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }
        Optional<PathBodyIdPair> pairOpt = findMatchingPathAndBodyId(data);
        if (pairOpt.isEmpty()) {
            logger.skip("No matching URL path parameter and top-level body identifier field found");
            return;
        }
        PathBodyIdPair pair = pairOpt.get();
        Object currentBodyId = JsonUtils.getVariableFromJson(data.getPayload(), pair.bodyField());
        Schema<?> schema = data.getRequestPropertyTypes() == null ? null : data.getRequestPropertyTypes().get(pair.bodyField());
        Object mismatchedBodyId = selectMismatchedId(currentBodyId, schema);
        String mutatedPayload = CatsUtil.justReplaceField(data.getPayload(), pair.bodyField(), mismatchedBodyId).json();

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .payload(mutatedPayload)
                        .mutationTarget(RequestTarget.body(pair.bodyField()))
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .scenario("Send body identifier [%s=%s] that contradicts URL path parameter [{%s}] to verify path-vs-body ID consistency"
                                .formatted(pair.bodyField(), mismatchedBodyId, pair.pathParam()))
                        .responseProcessor((response, fuzzData) -> processResponse(response, fuzzData, pair, mismatchedBodyId))
                        .build()
        );
    }

    private void processResponse(CatsResponse response, FuzzingData data, PathBodyIdPair pair, Object mismatchedBodyId) {
        int code = response.getResponseCode();
        if (ResponseCodeFamily.is4xxCode(code)) {
            testCaseListener.reportResultInfo(logger, data, "Path and body ID mismatch rejected",
                    "The API properly rejected the contradicting body identifier [%s] (status %d)"
                            .formatted(pair.bodyField(), code));
            return;
        }
        if (ResponseCodeFamily.is2xxCode(code) && isMismatchedIdReflected(response.getBody(), pair.bodyField(), mismatchedBodyId)) {
            testCaseListener.reportResultError(logger, data,
                    CatsResultFactory.Reason.PATH_BODY_ID_MISMATCH_ACCEPTED.value(),
                    "Body identifier [%s=%s] contradicting URL path parameter [{%s}] was accepted and reflected in the response (status %d)"
                            .formatted(pair.bodyField(), mismatchedBodyId, pair.pathParam(), code));
            return;
        }
        if (ResponseCodeFamily.is5xxCode(code)) {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.SERVER_ERROR.value(),
                    "Server returned %d error when body identifier [%s] contradicted URL path parameter [{%s}]"
                            .formatted(code, pair.bodyField(), pair.pathParam()));
            return;
        }
        testCaseListener.reportResultInfo(logger, data, "Contradicting body identifier ignored by server",
                "Update succeeded (status %d) without reflecting the mismatched body identifier [%s=%s]"
                        .formatted(code, pair.bodyField(), mismatchedBodyId));
    }

    static boolean isMismatchedIdReflected(String responseBody, String bodyField, Object mismatchedBodyId) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String bodyLower = responseBody.toLowerCase(Locale.ROOT);
        String fieldLower = bodyField.toLowerCase(Locale.ROOT);
        String valLower = String.valueOf(mismatchedBodyId).toLowerCase(Locale.ROOT);
        return (bodyLower.contains("\"" + fieldLower + "\"") || bodyLower.contains("\"id\""))
                && bodyLower.contains(valLower);
    }

    static Object selectMismatchedId(Object currentBodyId, Schema<?> schema) {
        String currentStr = currentBodyId == null ? "" : String.valueOf(currentBodyId);
        if (currentBodyId instanceof Number num) {
            return num.longValue() == NUMERIC_CANARY_PRIMARY ? NUMERIC_CANARY_FALLBACK : NUMERIC_CANARY_PRIMARY;
        }
        if (CatsModelUtils.isIntegerSchema(schema) || CatsModelUtils.isNumberSchema(schema)
                || DIGITS_PATTERN.matcher(currentStr).matches()) {
            return String.valueOf(NUMERIC_CANARY_PRIMARY).equals(currentStr) ? NUMERIC_CANARY_FALLBACK : NUMERIC_CANARY_PRIMARY;
        }
        if (CatsModelUtils.isUUIDSchema(schema) || UUID_PATTERN.matcher(currentStr).matches()) {
            return UUID_CANARY_PRIMARY.equalsIgnoreCase(currentStr) ? UUID_CANARY_FALLBACK : UUID_CANARY_PRIMARY;
        }
        return STRING_CANARY.equals(currentStr) ? STRING_CANARY + "-2" : STRING_CANARY;
    }

    static Optional<PathBodyIdPair> findMatchingPathAndBodyId(FuzzingData data) {
        if (data.getPath() == null) {
            return Optional.empty();
        }
        Set<String> pathVariables = OpenApiUtils.getPathVariables(data.getPath());
        if (pathVariables.isEmpty()) {
            return Optional.empty();
        }
        List<String> topLevelBodyFields = Optional.ofNullable(data.getAllFieldsByHttpMethod()).orElse(Set.of()).stream()
                .filter(field -> !field.contains("#"))
                .filter(field -> JsonUtils.isFieldInJson(data.getPayload(), field))
                .sorted(Comparator.naturalOrder())
                .toList();

        for (String pathVar : pathVariables) {
            String normalizedPathVar = WordUtils.normalizeAlphaNumeric(pathVar);
            Optional<String> exactMatch = topLevelBodyFields.stream()
                    .filter(f -> WordUtils.normalizeAlphaNumeric(f).equals(normalizedPathVar))
                    .findFirst();
            if (exactMatch.isPresent()) {
                return Optional.of(new PathBodyIdPair(pathVar, exactMatch.get()));
            }
            if (normalizedPathVar.endsWith("id")) {
                Optional<String> genericIdMatch = topLevelBodyFields.stream()
                        .filter(f -> "id".equals(WordUtils.normalizeAlphaNumeric(f)))
                        .findFirst();
                if (genericIdMatch.isPresent()) {
                    return Optional.of(new PathBodyIdPair(pathVar, genericIdMatch.get()));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "sends a body identifier that contradicts the URL path parameter on write requests to detect split-brain updates and BOLA";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
