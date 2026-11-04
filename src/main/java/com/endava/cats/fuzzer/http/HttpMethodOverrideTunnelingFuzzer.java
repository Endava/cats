package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.PathItem;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Sends documented {@code POST} requests carrying HTTP method-override headers
 * ({@code X-HTTP-Method-Override}, {@code X-Method-Override}, {@code X-HTTP-Method})
 * set to an undocumented verb (e.g. {@code DELETE}, {@code PUT}) or invalid verb
 * to detect OWASP API5:2023 HTTP verb tunneling and filter crashes.
 */
@Singleton
@HttpFuzzer
public class HttpMethodOverrideTunnelingFuzzer implements Fuzzer {
    static final List<String> OVERRIDE_HEADERS = List.of(
            "X-HTTP-Method-Override", "X-Method-Override", "X-HTTP-Method");
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final Set<String> fuzzedPaths = new HashSet<>();
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    public HttpMethodOverrideTunnelingFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getMethod() != HttpMethod.POST) {
            return;
        }
        if (!fuzzedPaths.add(data.getPath())) {
            logger.skip("Skip path {} as method-override tunneling was already tested", data.getPath());
            return;
        }
        String targetVerb = selectUndocumentedVerb(data.getPathItem()).orElse("CATS_OVERRIDE");
        OVERRIDE_HEADERS.forEach(headerName -> sendOverrideProbe(data, headerName, targetVerb));
    }

    private void sendOverrideProbe(FuzzingData data, String headerName, String targetVerb) {
        List<CatsHeader> headers = buildHeadersWithOverride(data.getHeaders(), headerName, targetVerb);
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .headers(headers)
                        .payload(data.getPayload())
                        .mutationTarget(RequestTarget.header(headerName))
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                        .scenario("Send POST with %s: %s to test HTTP verb tunneling to undocumented method"
                                .formatted(headerName, targetVerb))
                        .responseProcessor((response, fuzzData) -> processResponse(response, fuzzData, headerName, targetVerb))
                        .build());
    }

    static Optional<String> selectUndocumentedVerb(PathItem pathItem) {
        if (pathItem == null) {
            return Optional.of("DELETE");
        }
        if (pathItem.getDelete() == null) {
            return Optional.of("DELETE");
        }
        if (pathItem.getPut() == null) {
            return Optional.of("PUT");
        }
        if (pathItem.getPatch() == null) {
            return Optional.of("PATCH");
        }
        return Optional.empty();
    }

    private static List<CatsHeader> buildHeadersWithOverride(Set<CatsHeader> baseHeaders, String headerName, String verb) {
        List<CatsHeader> headers = baseHeaders == null ? new ArrayList<>() : new ArrayList<>(baseHeaders);
        headers.removeIf(h -> h.getName().equalsIgnoreCase(headerName));
        headers.add(CatsHeader.from(headerName, verb, false, null));
        return headers;
    }

    private void processResponse(CatsResponse response, FuzzingData data, String headerName, String targetVerb) {
        int code = response.getResponseCode();
        if (ResponseCodeFamily.is5xxCode(code)) {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.SERVER_ERROR.value(),
                    "Server returned %d error when processing %s: %s".formatted(code, headerName, targetVerb));
            return;
        }
        if (code == 204 && "DELETE".equals(targetVerb)) {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.HTTP_METHOD_OVERRIDE_ACCEPTED.value(),
                    "POST request with %s: DELETE returned 204 No Content, indicating undocumented DELETE was executed via verb tunneling"
                            .formatted(headerName));
            return;
        }
        testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.FOURXX_TWOXX);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "sends POST requests with HTTP method-override headers targeting undocumented verbs to detect verb tunneling";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
