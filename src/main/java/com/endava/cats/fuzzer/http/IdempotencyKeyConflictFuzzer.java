package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verifies that endpoints declaring an idempotency header reject a replayed Idempotency-Key
 * when sent with a different request payload (expecting 4XX such as 409 Conflict or 422 Unprocessable Entity).
 */
@Singleton
@HttpFuzzer
public class IdempotencyKeyConflictFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(IdempotencyKeyConflictFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    /**
     * Creates a new IdempotencyKeyConflictFuzzer instance.
     *
     * @param simpleExecutor   the executor used to run HTTP calls
     * @param testCaseListener the test case listener for reporting results
     */
    public IdempotencyKeyConflictFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }

        Optional<CatsHeader> idempotencyHeader = findIdempotencyHeader(data.getHeaders());
        if (idempotencyHeader.isEmpty()) {
            logger.skip("No idempotency header declared on operation {} {}", data.getMethod(), data.getPath());
            return;
        }

        Optional<String> mutatedPayload = buildMutatedPayload(data);
        if (mutatedPayload.isEmpty()) {
            logger.skip("Could not construct a distinct mutated payload for idempotency conflict check");
            return;
        }

        String sharedKey = UUID.randomUUID().toString();
        String headerName = idempotencyHeader.get().getName();
        List<CatsHeader> headersWithSharedKey = replaceHeaderValue(data.getHeaders(), headerName, sharedKey);

        AtomicBoolean firstSucceeded = new AtomicBoolean(false);
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .headers(headersWithSharedKey)
                        .payload(data.getPayload())
                        .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                        .scenario("Send initial request with %s=%s before replaying the same key with a different payload"
                                .formatted(headerName, sharedKey))
                        .responseProcessor((response, context) -> {
                            boolean ok = ResponseCodeFamily.is2xxCode(response.getResponseCode());
                            firstSucceeded.set(ok);
                            if (ok) {
                                testCaseListener.reportResultInfo(logger, context,
                                        "Initial request with {} succeeded with status {}", headerName, response.responseCodeAsString());
                            } else if (ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
                                testCaseListener.reportResult(logger, context, response, ResponseCodeFamilyPredefined.TWOXX);
                            } else {
                                testCaseListener.reportResultInfo(logger, context,
                                        "Initial request with {} returned {}; skipping conflict replay", headerName, response.responseCodeAsString());
                            }
                        })
                        .build()
        );

        if (!firstSucceeded.get()) {
            return;
        }

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .headers(headersWithSharedKey)
                        .payload(mutatedPayload.get())
                        .mutationTarget(RequestTarget.header(headerName))
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .scenario("Replay the same %s=%s with a modified request body to verify idempotency conflict detection"
                                .formatted(headerName, sharedKey))
                        .build()
        );
    }

    private static Optional<CatsHeader> findIdempotencyHeader(Set<CatsHeader> headers) {
        if (headers == null) {
            return Optional.empty();
        }
        return headers.stream()
                .filter(h -> h != null && h.getName() != null
                        && WordUtils.normalizeAlphaNumeric(h.getName()).contains("idempotency"))
                .findFirst();
    }

    private static List<CatsHeader> replaceHeaderValue(Set<CatsHeader> headers, String targetName, String value) {
        List<CatsHeader> copy = new ArrayList<>();
        for (CatsHeader header : headers) {
            CatsHeader cloned = header.copy();
            if (targetName.equalsIgnoreCase(cloned.getName())) {
                cloned.withValue(value);
            }
            copy.add(cloned);
        }
        return copy;
    }

    private static Optional<String> buildMutatedPayload(FuzzingData data) {
        Set<String> fields = data.getAllFieldsByHttpMethod();
        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }
        List<String> sortedFields = new ArrayList<>(fields);
        sortedFields.sort(String::compareTo);
        sortedFields.sort((a, b) -> Boolean.compare(!isUnconstrainedStringField(data, a), !isUnconstrainedStringField(data, b)));
        for (String field : sortedFields) {
            Object currentVal = JsonUtils.getVariableFromJson(data.getPayload(), field);
            if (!JsonUtils.isNotSet(String.valueOf(currentVal)) && !(currentVal instanceof Boolean)) {
                Object newVal = currentVal instanceof Number num
                        ? num.longValue() + 1L
                        : String.valueOf(currentVal) + "_conflict";
                String replaced = CatsUtil.justReplaceField(data.getPayload(), field, newVal).json();
                if (!replaced.equals(data.getPayload())) {
                    return Optional.of(replaced);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isUnconstrainedStringField(FuzzingData data, String field) {
        if (data.getRequestPropertyTypes() == null) {
            return false;
        }
        var schema = data.getRequestPropertyTypes().get(field);
        return CatsModelUtils.isStringSchema(schema)
                && schema.getEnum() == null
                && schema.getPattern() == null
                && schema.getFormat() == null;
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.PUT);
    }

    @Override
    public String description() {
        return "replays the same Idempotency-Key header with a modified request payload to verify the server rejects conflicting replays with 4XX";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
