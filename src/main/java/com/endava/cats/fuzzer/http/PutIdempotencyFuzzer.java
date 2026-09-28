package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.RuntimeResourcePool;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Repeats the same successful PUT twice on a resource created in this run and compares the two GET representations.
 * Both GETs must succeed before the comparison can establish whether repeated PUTs changed resource state.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class PutIdempotencyFuzzer implements Fuzzer {
    private static final Set<String> VOLATILE_FIELDS = Set.of("updatedAt", "modifiedAt", "version", "revision");
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(PutIdempotencyFuzzer.class);
    private final RuntimeResourcePool pool;
    private final ApiArguments arguments;
    private final FilterArguments filterArguments;
    private final SimpleExecutor executor;
    private final TestCaseListener listener;

    /** Creates a bounded PUT replay check using only resources owned by the current run. */
    public PutIdempotencyFuzzer(RuntimeResourcePool pool, ApiArguments arguments, FilterArguments filterArguments,
                                SimpleExecutor executor, TestCaseListener listener) {
        this.pool = pool;
        this.arguments = arguments;
        this.filterArguments = filterArguments;
        this.executor = executor;
        this.listener = listener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!hasInScopeReadWithoutConditionalHeaders(data)) {
            logger.skip("PUT idempotency needs an in-scope item GET without conditional headers for {}", data.getPath());
            return;
        }
        Optional<OwnedPut> baseline = latestOwnedSuccessfulPut(data);
        if (baseline.isEmpty()) {
            logger.skip("No successful PUT to a resource created by POST 201 Location was observed for {}", data.getPath());
            return;
        }
        checkRepeatedPut(data, baseline.get());
    }

    private boolean hasInScopeReadWithoutConditionalHeaders(FuzzingData data) {
        return data.getMethod() == HttpMethod.PUT && data.getPathItem() != null && data.getPathItem().getGet() != null &&
                filterArguments.isHttpMethodSupplied(HttpMethod.GET) && data.getHeaders() != null &&
                data.getHeaders().stream().noneMatch(header -> header.getName().equalsIgnoreCase("If-Match") ||
                        header.getName().equalsIgnoreCase("If-None-Match"));
    }

    /** Finds the last usable PUT whose URL matches a POST 201 Location from this run. */
    private Optional<OwnedPut> latestOwnedSuccessfulPut(FuzzingData data) {
        return pool.successfulExchanges(data.getPath(), HttpMethod.PUT).reversed().stream()
                .filter(exchange -> JsonUtils.isValidJson(exchange.requestBody()))
                .map(exchange -> ObservedHttpTarget.ownedPath(pool, arguments, data.getPath(), exchange.url())
                        .map(path -> new OwnedPut(path, exchange.requestBody())))
                .flatMap(Optional::stream).findFirst();
    }

    /** Stops the sequence as soon as a step fails, preventing further writes to an unverified resource. */
    private void checkRepeatedPut(FuzzingData data, OwnedPut baseline) {
        if (!repeatPut(data, baseline)) {
            return;
        }
        Optional<JsonObject> before = readAfterPut(data, baseline.path(), null);
        if (before.isEmpty()) {
            return;
        }
        if (repeatPut(data, baseline)) {
            readAfterPut(data, baseline.path(), before.get());
        }
    }

    private boolean repeatPut(FuzzingData data, OwnedPut baseline) {
        AtomicBoolean succeeded = new AtomicBoolean();
        executor.execute(SimpleExecutorContext.builder().fuzzer(this).logger(logger).fuzzingData(data)
                .path(baseline.path()).payload(baseline.body()).replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Repeat a previously successful PUT on a resource created in this run")
                .responseProcessor((response, context) -> {
                    succeeded.set(isSuccessful(response));
                    reportPutResponse(response, context, succeeded.get());
                }).build());
        return succeeded.get();
    }

    private void reportPutResponse(CatsResponse response, FuzzingData data, boolean succeeded) {
        if (response.getResponseCode() >= 500 && response.getResponseCode() < 600) {
            listener.reportResultError(logger, data, "PUT retry caused server error", "Repeating a successful PUT returned {}", response.responseCodeAsString());
        } else if (!succeeded) {
            listener.reportResultWarn(logger, data, "PUT retry rejected", "Repeating a successful PUT returned {}", response.responseCodeAsString());
        } else {
            listener.reportResultInfo(logger, data, "Repeated PUT returned {}", response.responseCodeAsString());
        }
    }

    /** Returns a comparable state snapshot, if the GET response contains a complete JSON object. */
    private Optional<JsonObject> readAfterPut(FuzzingData data, String path, JsonObject before) {
        AtomicReference<JsonObject> snapshot = new AtomicReference<>();
        executor.execute(SimpleExecutorContext.builder().fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.GET).path(path).payload("{}").replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Compare resource state after a repeated identical PUT")
                .responseProcessor((response, context) -> reportReadResponse(response, context, before, snapshot))
                .build());
        return Optional.ofNullable(snapshot.get());
    }

    /** Compares stable JSON fields from the GET after the first and second identical PUTs. */
    private void reportReadResponse(CatsResponse response, FuzzingData data, JsonObject before,
                                    AtomicReference<JsonObject> snapshot) {
        if (response.getResponseCode() >= 500 && response.getResponseCode() < 600) {
            listener.reportResultError(logger, data, "PUT retry broke resource read", "GET after PUT returned {}", response.responseCodeAsString());
            return;
        }
        if (!isComparableGet(response)) {
            listener.reportResultInfo(logger, data, "Cannot compare PUT state (GET returned {})", response.responseCodeAsString());
            return;
        }
        JsonElement state = JsonUtils.parseAsJsonElement(response.getBody());
        if (!state.isJsonObject()) {
            listener.reportResultInfo(logger, data, "PUT state comparison requires an object response");
            return;
        }
        JsonObject stable = state.getAsJsonObject().deepCopy();
        VOLATILE_FIELDS.forEach(stable::remove);
        snapshot.set(stable);
        if (before == null) {
            listener.reportResultInfo(logger, data, "Captured resource state after the first repeated PUT");
        } else if (!before.equals(stable)) {
            listener.reportResultWarn(logger, data, "PUT is not idempotent", "Resource state changed after repeating the identical PUT");
        } else {
            listener.reportResultInfo(logger, data, "Resource state did not change after repeating the identical PUT");
        }
    }

    private boolean isSuccessful(CatsResponse response) {
        return response.getResponseCode() >= 200 && response.getResponseCode() < 300;
    }

    private boolean isComparableGet(CatsResponse response) {
        return isSuccessful(response) && !response.isBodyTruncated() && JsonUtils.isValidJson(response.getBody());
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "repeat a successful PUT twice on a resource created in the current run and compare resulting GET state";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }

    private record OwnedPut(String path, String body) {
    }
}
