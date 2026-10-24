package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Probes a previously observed child under another observed parent on the same nested GET operation.
 * A successful response is only a heuristic warning, not proof of cross-account authorization failure.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class CrossParentResourceIsolationFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CrossParentResourceIsolationFuzzer.class);
    private final RuntimeResourcePool pool;
    private final ApiArguments arguments;
    private final SimpleExecutor executor;
    private final TestCaseListener listener;

    /** Creates an isolation probe that only uses successful same-origin GET exchanges. */
    public CrossParentResourceIsolationFuzzer(RuntimeResourcePool pool, ApiArguments arguments,
                                              SimpleExecutor executor, TestCaseListener listener) {
        this.pool = pool;
        this.arguments = arguments;
        this.executor = executor;
        this.listener = listener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getMethod() != HttpMethod.GET) {
            return;
        }
        Optional<ParentChildPath> hierarchy = parentChildPath(data.getPath());
        if (hierarchy.isEmpty()) {
            logger.skip("GET {} does not contain a parent and a child path parameter", data.getPath());
            return;
        }
        List<List<String>> observed = pool.successfulExchanges(data.getPath(), HttpMethod.GET).stream()
                .map(exchange -> ObservedHttpTarget.relativeRequestPath(arguments, exchange.url()))
                .flatMap(Optional::stream)
                .filter(path -> ObservedHttpTarget.matchesTemplate(data.getPath(), path))
                .map(path -> Arrays.asList(path.split("/", -1)))
                .distinct().toList();
        Optional<SwappedChild> candidate = firstObservedChildUnderDifferentParent(observed, hierarchy.get())
                .or(() -> firstObservedChildWithAlternativePoolParent(data.getPath(), observed, hierarchy.get()));
        if (candidate.isEmpty()) {
            logger.skip("GET {} needs two successful children under different observed parents", data.getPath());
            return;
        }
        SwappedChild swap = candidate.get();
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).logger(logger).fuzzingData(data).path(swap.path()).payload("{}")
                .replaceRefData(false).replaceUrlParams(false).mutationTarget(RequestTarget.path(swap.parentName()))
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .scenario("Request a known child resource under a different observed parent")
                .responseProcessor((response, context) -> checkResponse(response, context, swap))
                .build());
    }

    /** Identifies the immediate parent and final child parameters of a nested item path. */
    private Optional<ParentChildPath> parentChildPath(String path) {
        List<String> segments = Arrays.asList(path.split("/", -1));
        List<Integer> variables = IntStream.range(0, segments.size())
                .filter(index -> segments.get(index).matches("\\{[^/]+}"))
                .boxed().toList();
        if (variables.size() < 2 || variables.getLast() != segments.size() - 1) {
            return Optional.empty();
        }
        int parentIndex = variables.get(variables.size() - 2);
        int childIndex = variables.getLast();
        return Optional.of(new ParentChildPath(parentIndex, childIndex,
                parameterName(segments.get(parentIndex)), parameterName(segments.get(childIndex))));
    }

    private String parameterName(String segment) {
        return segment.substring(1, segment.length() - 1);
    }

    /** Chooses one pair with distinct parent and child IDs while preserving any outer ancestors. */
    private Optional<SwappedChild> firstObservedChildUnderDifferentParent(List<List<String>> observed, ParentChildPath path) {
        return observed.stream().flatMap(child -> observed.stream()
                        .filter(other -> hasDifferentParentAndChild(child, other, path))
                        .map(other -> new SwappedChild(swappedPath(child, other, path.parentIndex()),
                                path.parentName(), path.childName(), child.get(path.childIndex()))))
                .findFirst();
    }

    private Optional<SwappedChild> firstObservedChildWithAlternativePoolParent(String templatePath,
                                                                               List<List<String>> observed,
                                                                               ParentChildPath path) {
        for (List<String> child : observed) {
            String currentParent = child.get(path.parentIndex());
            Optional<String> alternativeParent = Optional.ofNullable(
                            pool.alternativePathParameterValue(templatePath, path.parentName(), currentParent))
                    .orElse(Optional.empty())
                    .filter(parent -> !parent.equals(currentParent));
            if (alternativeParent.isPresent()) {
                List<String> segments = new ArrayList<>(child);
                segments.set(path.parentIndex(), alternativeParent.get());
                return Optional.of(new SwappedChild(String.join("/", segments),
                        path.parentName(), path.childName(), child.get(path.childIndex())));
            }
        }
        return Optional.empty();
    }

    private boolean hasDifferentParentAndChild(List<String> child, List<String> other, ParentChildPath path) {
        if (child.get(path.parentIndex()).equals(other.get(path.parentIndex())) ||
                child.get(path.childIndex()).equals(other.get(path.childIndex()))) {
            return false;
        }
        for (int index = 0; index < path.childIndex(); index++) {
            if (index != path.parentIndex() && !child.get(index).equals(other.get(index))) {
                return false;
            }
        }
        return true;
    }

    private String swappedPath(List<String> child, List<String> other, int parentIndex) {
        List<String> segments = new ArrayList<>(child);
        segments.set(parentIndex, other.get(parentIndex));
        return String.join("/", segments);
    }

    private void checkResponse(CatsResponse response, FuzzingData data, SwappedChild swap) {
        int code = response.getResponseCode();
        if (code == 403 || code == 404) {
            listener.reportResultInfo(logger, data, "Child resource unavailable under a different parent (status {})", code);
        } else if (ResponseCodeFamily.is5xxCode(code)) {
            listener.reportResultError(logger, data, Reason.CROSS_PARENT_SERVER_ERROR.value(),
                    "Request for a known child under a different parent returned {}", code);
        } else if (responseContainsObservedChild(response, swap)) {
            listener.reportResultWarn(logger, data, Reason.CROSS_PARENT_RESOURCE_EXPOSURE.value(),
                    "A known child identifier was returned successfully under a different observed parent; verify whether this resource is meant to be shared");
        } else {
            listener.reportResultInfo(logger, data, "Cross-parent request inconclusive (status {})", code);
        }
    }

    /** Requires a complete successful JSON response that identifies the original child, not merely a 2xx status. */
    private boolean responseContainsObservedChild(CatsResponse response, SwappedChild swap) {
        return ResponseCodeFamily.is2xxCode(response.getResponseCode()) && !response.isBodyTruncated() &&
                JsonUtils.isValidJson(response.getBody()) &&
                containsChildId(JsonUtils.parseAsJsonElement(response.getBody()), swap.childName(), swap.childId());
    }

    private boolean containsChildId(JsonElement element, String childName, String childId) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream().limit(50).anyMatch(item -> containsChildId(item, childName, childId));
        }
        if (!element.isJsonObject()) {
            return false;
        }
        return element.getAsJsonObject().entrySet().stream().anyMatch(entry ->
                entry.getValue().isJsonPrimitive() && isChildIdentifierField(entry.getKey(), childName) &&
                        entry.getValue().getAsString().equals(childId));
    }

    private boolean isChildIdentifierField(String field, String childName) {
        return field.equalsIgnoreCase(childName) || field.toLowerCase(Locale.ROOT).equals("id");
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "probe a known child resource under another observed parent, reporting only confirmed identity exposure as a warning";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }

    private record ParentChildPath(int parentIndex, int childIndex, String parentName, String childName) {
    }

    private record SwappedChild(String path, String parentName, String childName, String childId) {
    }
}
