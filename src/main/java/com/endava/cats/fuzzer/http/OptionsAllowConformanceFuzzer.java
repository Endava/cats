package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.KeyValuePair;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.PathItem;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Sends one {@code OPTIONS} request per path and verifies that the returned {@code Allow} header lists exactly the
 * HTTP methods documented in the contract for that path (RFC 9110 sections 9.3.7 and 10.2.1).
 * {@code HEAD} and {@code OPTIONS} are ignored because HTTP servers commonly implement them implicitly.
 */
@Singleton
@HttpFuzzer
public class OptionsAllowConformanceFuzzer implements Fuzzer {
    private static final Set<String> IMPLICIT_METHODS = Set.of(HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());
    private static final Set<String> CORS_PREFLIGHT_HEADERS = Set.of(
            "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers");

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(OptionsAllowConformanceFuzzer.class);
    private final Set<String> checkedPaths = ConcurrentHashMap.newKeySet();
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    record AllowComparison(Set<String> undocumented, Set<String> missing) {
        boolean matches() {
            return undocumented.isEmpty() && missing.isEmpty();
        }
    }

    /**
     * Creates a new OptionsAllowConformanceFuzzer instance.
     *
     * @param simpleExecutor   the executor used to run HTTP calls
     * @param testCaseListener the test case listener for reporting results
     */
    public OptionsAllowConformanceFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getPathItem() == null) {
            logger.skip("OPTIONS Allow check needs the contract path item for {}", data.getPath());
            return;
        }
        if (!checkedPaths.add(data.getPath())) {
            logger.skip("OPTIONS Allow header already checked for path {}", data.getPath());
            return;
        }
        simpleExecutor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).fuzzingData(data).logger(logger)
                .httpMethod(HttpMethod.OPTIONS).payload("")
                .skippedHeaders(CORS_PREFLIGHT_HEADERS)
                .mutationTarget(RequestTarget.httpMethod())
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Send an OPTIONS request and verify the Allow header lists exactly the HTTP methods documented for the path")
                .responseProcessor(this::checkResponse)
                .build());
    }

    private void checkResponse(CatsResponse response, FuzzingData data) {
        if (ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
            testCaseListener.reportResultError(logger, data, Reason.SERVER_ERROR.value(),
                    "OPTIONS request returned {}", response.responseCodeAsString());
            return;
        }
        Optional<Set<String>> allowed = allowedMethods(response);
        if (allowed.isEmpty()) {
            testCaseListener.reportResultInfo(logger, data, "OPTIONS Allow check inconclusive (status {}, no Allow header)",
                    response.responseCodeAsString());
            return;
        }
        reportComparison(data, compare(allowed.get(), documentedMethods(data.getPathItem())));
    }

    private void reportComparison(FuzzingData data, AllowComparison comparison) {
        if (comparison.matches()) {
            testCaseListener.reportResultInfo(logger, data, "OPTIONS Allow header matches the documented HTTP methods");
            return;
        }
        testCaseListener.reportResultWarn(logger, data, Reason.OPTIONS_ALLOW_MISMATCH.value(),
                "OPTIONS Allow header does not match the contract: undocumented methods {}, missing documented methods {}",
                comparison.undocumented(), comparison.missing());
    }

    static Optional<Set<String>> allowedMethods(CatsResponse response) {
        KeyValuePair<String, String> allowHeader = response.getHeader("Allow");
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode()) || allowHeader == null || allowHeader.getValue() == null) {
            return Optional.empty();
        }
        return Optional.of(Arrays.stream(allowHeader.getValue().split(","))
                .map(method -> method.trim().toUpperCase(Locale.ROOT))
                .filter(Predicate.not(String::isEmpty))
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    static Set<String> documentedMethods(PathItem pathItem) {
        return HttpMethod.OPERATIONS.entrySet().stream()
                .filter(entry -> entry.getValue().apply(pathItem) != null)
                .map(entry -> entry.getKey().name())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static AllowComparison compare(Set<String> allowed, Set<String> documented) {
        return new AllowComparison(difference(allowed, documented), difference(documented, allowed));
    }

    private static Set<String> difference(Set<String> source, Set<String> excluded) {
        return source.stream()
                .filter(Predicate.not(excluded::contains))
                .filter(Predicate.not(IMPLICIT_METHODS::contains))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String description() {
        return "send one OPTIONS request per path and warn if the Allow header lists undocumented HTTP methods or omits documented ones";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
