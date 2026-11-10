package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verifies RFC 9110 section 9.3.4 status-code semantics on {@code PUT} endpoints by first reading a resource
 * created in this run via {@code GET} to prove it exists, then sending a valid {@code PUT} to that same resource
 * and reporting an error if the server responds with {@code 201 Created} instead of {@code 200 OK} or
 * {@code 204 No Content}.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class MisleadingCreatePutFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(MisleadingCreatePutFuzzer.class);
    private final RuntimeResourcePool pool;
    private final ApiArguments arguments;
    private final FilterArguments filterArguments;
    private final SimpleExecutor executor;
    private final TestCaseListener listener;

    /**
     * Creates a misleading-create {@code PUT} check using only resources created in the current run.
     */
    public MisleadingCreatePutFuzzer(RuntimeResourcePool pool, ApiArguments arguments, FilterArguments filterArguments,
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
            logger.skip("Misleading create PUT check needs an in-scope item GET without conditional headers for {}", data.getPath());
            return;
        }
        ObservedHttpTarget.latestPutTarget(pool, arguments, data.getPath())
                .ifPresentOrElse(target -> verifyPutAfterGet(data, target),
                        () -> logger.skip("No successful PUT to a resource created in this run was observed for {}", data.getPath()));
    }

    private boolean hasInScopeReadWithoutConditionalHeaders(FuzzingData data) {
        return data.getMethod() == HttpMethod.PUT
                && data.getPathItem() != null
                && data.getPathItem().getGet() != null
                && filterArguments.isHttpMethodSupplied(HttpMethod.GET)
                && (data.getHeaders() == null || data.getHeaders().stream().noneMatch(MisleadingCreatePutFuzzer::isConditionalHeader));
    }

    private static boolean isConditionalHeader(CatsHeader header) {
        return header.getName().equalsIgnoreCase("If-Match") || header.getName().equalsIgnoreCase("If-None-Match");
    }

    private void verifyPutAfterGet(FuzzingData data, ObservedHttpTarget.OwnedWrite target) {
        if (resourceExistsOnGet(data, target.path())) {
            sendPutOnExistingResource(data, target);
        }
    }

    private boolean resourceExistsOnGet(FuzzingData data, String path) {
        AtomicBoolean exists = new AtomicBoolean();
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.GET).path(path).payload("{}")
                .replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Read existing resource before sending PUT to verify PUT does not return 201 Created")
                .responseProcessor((response, context) -> exists.set(classifyBaselineGet(response, context)))
                .build());
        return exists.get();
    }

    private boolean classifyBaselineGet(CatsResponse response, FuzzingData data) {
        if (ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
            listener.reportResultError(logger, data, Reason.SERVER_ERROR.value(),
                    "GET before PUT returned {}", response.responseCodeAsString());
            return false;
        }
        boolean readable = ResponseCodeFamily.is2xxCode(response.getResponseCode());
        listener.reportResultInfo(logger, data, readable
                ? "Confirmed resource exists before PUT (GET returned {})"
                : "Misleading create PUT check skipped: resource not readable before PUT (GET returned {})",
                response.responseCodeAsString());
        return readable;
    }

    private void sendPutOnExistingResource(FuzzingData data, ObservedHttpTarget.OwnedWrite target) {
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.PUT).path(target.path()).payload(target.body())
                .replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Send PUT to a resource proven to exist by a preceding GET and verify it does not return 201 Created")
                .responseProcessor(this::classifyPutResponse)
                .build());
    }

    private void classifyPutResponse(CatsResponse response, FuzzingData data) {
        if (ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
            listener.reportResultError(logger, data, Reason.SERVER_ERROR.value(),
                    "PUT on existing resource after GET returned {}", response.responseCodeAsString());
            return;
        }
        if (response.getResponseCode() == ResponseCodeFamily.STATUS_CREATED) {
            listener.reportResultError(logger, data, Reason.MISLEADING_CREATE_PUT.value(),
                    "PUT on an existing resource confirmed by a preceding 2xx GET returned 201 Created instead of 200 OK or 204 No Content");
            return;
        }
        listener.reportResultInfo(logger, data, "PUT on existing resource after GET returned {}", response.responseCodeAsString());
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "confirm a resource created in the current run exists via GET and verify a subsequent PUT does not return 201 Created";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
