package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.RuntimeResourcePool;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Uses previously observed ETags to check If-None-Match on GET and a stale If-Match on PUT/PATCH.
 * The write probe only targets a resource proven to have been created in this run.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class ConditionalEtagFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ConditionalEtagFuzzer.class);
    private final RuntimeResourcePool pool;
    private final ApiArguments arguments;
    private final SimpleExecutor executor;
    private final TestCaseListener listener;

    /** Creates conditional-request probes from successful exchanges recorded by the resource pool. */
    public ConditionalEtagFuzzer(RuntimeResourcePool pool, ApiArguments arguments,
                                 SimpleExecutor executor, TestCaseListener listener) {
        this.pool = pool;
        this.arguments = arguments;
        this.executor = executor;
        this.listener = listener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getMethod() == HttpMethod.GET) {
            checkConditionalGet(data);
        } else if (data.getMethod() == HttpMethod.PUT || data.getMethod() == HttpMethod.PATCH) {
            checkStaleWrite(data);
        }
    }

    /** Replays a matching successful GET with If-None-Match; skips if no reusable ETag was observed. */
    private void checkConditionalGet(FuzzingData data) {
        Optional<ObservedGet> baseline = pool.successfulExchanges(data.getPath(), HttpMethod.GET).reversed().stream()
                .map(exchange -> ObservedHttpTarget.relativePath(arguments, exchange.url())
                        .filter(path -> ObservedHttpTarget.matchesTemplate(data.getPath(), path))
                        .filter(path -> validTag(exchange.etag()))
                        .map(path -> new ObservedGet(path, exchange.etag())))
                .flatMap(Optional::stream).findFirst();
        if (baseline.isEmpty()) {
            logger.skip("No successful same-origin GET with an ETag was observed for {}", data.getPath());
            return;
        }
        ObservedGet observed = baseline.get();
        List<CatsHeader> headers = new ArrayList<>(data.getHeaders());
        headers.removeIf(header -> header.getName().equalsIgnoreCase("If-None-Match"));
        headers.add(CatsHeader.from("If-None-Match", observed.etag(), false, null));
        executor.execute(SimpleExecutorContext.builder().fuzzer(this).logger(logger).fuzzingData(data)
                .path(observed.path()).payload("{}").headers(headers).replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.THREEXX)
                .expectedSpecificResponseCode("304")
                .scenario("Repeat a successful GET with the observed ETag in If-None-Match")
                .responseProcessor((response, context) -> checkConditionalGetResponse(response, context, observed.etag()))
                .build());
    }

    /** Replays one owned update only when the same resource has both an older and a newer ETag. */
    private void checkStaleWrite(FuzzingData data) {
        if (!hasItemReadWithoutExplicitIfMatch(data)) {
            logger.skip("Stale If-Match check requires a GET operation without an explicit If-Match header for {}", data.getPath());
            return;
        }
        Optional<StaleTagPair> tags = observedStaleTagPair(data);
        if (tags.isEmpty()) {
            logger.skip("No two distinct ETags for the same GET resource were observed for {}", data.getPath());
            return;
        }
        Optional<OwnedWrite> write = latestWriteToCreatedResource(data, tags.get().url());
        if (write.isEmpty()) {
            logger.skip("No successful {} to the ETag resource created by POST 201 Location was observed for {}",
                    data.getMethod(), data.getPath());
            return;
        }
        List<CatsHeader> headers = new ArrayList<>(data.getHeaders());
        headers.add(CatsHeader.from("If-Match", tags.get().oldTag(), false, null));
        executor.execute(SimpleExecutorContext.builder().fuzzer(this).logger(logger).fuzzingData(data)
                .path(write.get().path()).payload(write.get().body()).headers(headers)
                .replaceRefData(false).replaceUrlParams(false).mutationTarget(RequestTarget.header("If-Match"))
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .expectedSpecificResponseCode("412")
                .scenario("Replay an update to a resource created in this run using an observed stale ETag")
                .responseProcessor(this::checkStaleWriteResponse)
                .build());
    }

    private boolean hasItemReadWithoutExplicitIfMatch(FuzzingData data) {
        return data.getHeaders() != null && data.getHeaders().stream()
                .noneMatch(header -> header.getName().equalsIgnoreCase("If-Match")) &&
                data.getPathItem() != null && data.getPathItem().getGet() != null;
    }

    /** Finds a newer and an older, different ETag for exactly the same observed URL. */
    private Optional<StaleTagPair> observedStaleTagPair(FuzzingData data) {
        List<RuntimeResourcePool.SuccessfulExchange> gets = pool.successfulExchanges(data.getPath(), HttpMethod.GET);
        for (int index = gets.size() - 1; index > 0; index--) {
            RuntimeResourcePool.SuccessfulExchange current = gets.get(index);
            if (!validTag(current.etag())) {
                continue;
            }
            Optional<RuntimeResourcePool.SuccessfulExchange> older = gets.subList(0, index).reversed().stream()
                    .filter(previous -> hasDifferentEtagForSameUrl(current, previous))
                    .findFirst();
            if (older.isPresent()) {
                return Optional.of(new StaleTagPair(current.url(), older.get().etag()));
            }
        }
        return Optional.empty();
    }

    private boolean hasDifferentEtagForSameUrl(RuntimeResourcePool.SuccessfulExchange current,
                                               RuntimeResourcePool.SuccessfulExchange previous) {
        return current.url().equals(previous.url()) && validTag(previous.etag()) &&
                !current.etag().equals(previous.etag());
    }

    /** Returns a successful write to the ETagged URL only when a POST 201 Location proves ownership. */
    private Optional<OwnedWrite> latestWriteToCreatedResource(FuzzingData data, String observedUrl) {
        return pool.successfulExchanges(data.getPath(), data.getMethod()).reversed().stream()
                .filter(write -> write.url().equals(observedUrl) && JsonUtils.isValidJson(write.requestBody()))
                .map(write -> ObservedHttpTarget.ownedPath(pool, arguments, data.getPath(), write.url())
                        .map(path -> new OwnedWrite(path, write.requestBody())))
                .flatMap(Optional::stream).findFirst();
    }

    private boolean validTag(String tag) {
        return tag != null && !tag.isBlank() && !tag.contains("\r") && !tag.contains("\n");
    }

    private void checkStaleWriteResponse(CatsResponse response, FuzzingData data) {
        int code = response.getResponseCode();
        if (code == 412 || code == 409) {
            listener.reportResultInfo(logger, data, "Stale If-Match was rejected (status {})", code);
        } else if (code >= 200 && code < 300) {
            listener.reportResultWarn(logger, data, "Stale If-Match accepted",
                    "An update with a previously observed stale ETag succeeded. Check whether the precondition was honored");
        } else if (code >= 500 && code < 600) {
            listener.reportResultError(logger, data, "Stale If-Match caused server error", "An update with a stale ETag returned {}", code);
        } else {
            listener.reportResultInfo(logger, data, "Stale If-Match check inconclusive (status {})", code);
        }
    }

    private void checkConditionalGetResponse(CatsResponse response, FuzzingData data, String sentTag) {
        if (response.getResponseCode() == 304) {
            listener.reportResultInfo(logger, data, "If-None-Match returned 304 for an unchanged ETag");
        } else if (response.getResponseCode() == 200 && response.getHeader("ETag") != null &&
                sentTag.equals(response.getHeader("ETag").getValue())) {
            listener.reportResultWarn(logger, data, "Conditional GET ignored", "A GET with If-None-Match returned 200 with the same ETag");
        } else if (response.getResponseCode() >= 500 && response.getResponseCode() < 600) {
            listener.reportResultError(logger, data, "Conditional GET caused server error", "A GET with If-None-Match returned {}", response.responseCodeAsString());
        } else {
            listener.reportResultInfo(logger, data, "Conditional GET inconclusive (status {})", response.responseCodeAsString());
        }
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.POST, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "test If-None-Match on GET and stale If-Match on PUT/PATCH with previously observed ETags";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }

    private record ObservedGet(String path, String etag) {
    }

    private record StaleTagPair(String url, String oldTag) {
    }

    private record OwnedWrite(String path, String body) {
    }
}
