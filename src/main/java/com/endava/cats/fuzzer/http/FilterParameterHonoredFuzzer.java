package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
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
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Metamorphic check that a {@code GET} collection filtered by an exact-match query parameter (enum, boolean, integer
 * or uuid) only returns items whose field with the same name holds the requested value (filtered result is a subset
 * of the unfiltered collection). Filter values are taken from items observed earlier in the run or from the contract.
 */
@Singleton
@HttpFuzzer
@SecondPhaseFuzzer
public class FilterParameterHonoredFuzzer implements Fuzzer {
    private static final Set<String> NON_FILTER_PARAMS = Set.of(
            "limit", "offset", "page", "pagenumber", "pagesize", "perpage", "size", "skip", "top", "start", "count",
            "cursor", "token", "pagetoken", "nextpagetoken", "sort", "sortby", "order", "orderby", "direction",
            "q", "query", "search", "filter", "fields", "expand", "include", "format", "lang", "locale");
    private static final int MAX_REPORTED_MISMATCHES = 3;

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FilterParameterHonoredFuzzer.class);
    private final RuntimeResourcePool pool;
    private final SimpleExecutor executor;
    private final TestCaseListener listener;

    record FilterProbe(String param, JsonPrimitive value) {
        String displayValue() {
            return value.getAsString();
        }
    }

    record FilterVerdict(int compared, List<String> mismatches) {
    }

    /**
     * Creates a filter-honored check that only reuses values observed in the current run or declared in the contract.
     */
    public FilterParameterHonoredFuzzer(RuntimeResourcePool pool, SimpleExecutor executor, TestCaseListener listener) {
        this.pool = pool;
        this.executor = executor;
        this.listener = listener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Optional<FilterProbe> probe = isCollectionRead(data) ? selectProbe(data) : Optional.empty();
        probe.ifPresentOrElse(filter -> sendFilteredRead(data, filter), () -> logger.skip(
                "No exact-match filter query parameter with a known value on {} {}", data.getMethod(), data.getPath()));
    }

    private void sendFilteredRead(FuzzingData data, FilterProbe filter) {
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).fuzzingData(data).logger(logger)
                .payload(CatsUtil.justReplaceField(data.getPayload(), filter.param(), toJavaValue(filter.value())).json())
                .mutationTarget(RequestTarget.query(filter.param()))
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Send GET filtered by [%s=%s] and verify every returned item matches the filter"
                        .formatted(filter.param(), filter.displayValue()))
                .responseProcessor((response, context) -> judge(response, context, filter))
                .build());
    }

    private static boolean isCollectionRead(FuzzingData data) {
        return data.getMethod() == HttpMethod.GET && data.getPath() != null && !data.getPath().endsWith("}");
    }

    Optional<FilterProbe> selectProbe(FuzzingData data) {
        return Optional.ofNullable(data.getQueryParams()).orElse(Set.of()).stream()
                .sorted()
                .filter(param -> isExactMatchFilter(data, param))
                .map(param -> filterValue(data, param).map(value -> new FilterProbe(param, value)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    static boolean isExactMatchFilter(FuzzingData data, String param) {
        Schema<?> schema = data.getRequestPropertyTypes() == null ? null : data.getRequestPropertyTypes().get(param);
        return schema != null && !NON_FILTER_PARAMS.contains(WordUtils.normalizeLeaf(param))
                && JsonUtils.isFieldInJson(data.getPayload(), param) && isExactMatchType(schema);
    }

    private static boolean isExactMatchType(Schema<?> schema) {
        return CatsModelUtils.isEnumSchema(schema) || CatsModelUtils.isBooleanSchema(schema)
                || CatsModelUtils.isIntegerSchema(schema)
                || (CatsModelUtils.isStringSchema(schema) && "uuid".equalsIgnoreCase(schema.getFormat()));
    }

    private Optional<JsonPrimitive> filterValue(FuzzingData data, String param) {
        return observedValue(data.getPath(), param)
                .or(() -> declaredValue(data.getRequestPropertyTypes().get(param)));
    }

    private Optional<JsonPrimitive> observedValue(String path, String param) {
        return pool.successfulExchanges(path, HttpMethod.GET).reversed().stream()
                .map(RuntimeResourcePool.SuccessfulExchange::responseBody)
                .filter(JsonUtils::isValidJson)
                .flatMap(body -> fieldValues(JsonUtils.parseAsJsonElement(body), param))
                .findFirst();
    }

    static Optional<JsonPrimitive> declaredValue(Schema<?> schema) {
        if (CatsModelUtils.isEnumSchema(schema)) {
            return schema.getEnum().stream().filter(Objects::nonNull).findFirst().map(FilterParameterHonoredFuzzer::toPrimitive);
        }
        return CatsModelUtils.isBooleanSchema(schema) ? Optional.of(new JsonPrimitive(true)) : Optional.empty();
    }

    private static JsonPrimitive toPrimitive(Object value) {
        return switch (value) {
            case Number number -> new JsonPrimitive(number);
            case Boolean bool -> new JsonPrimitive(bool);
            default -> new JsonPrimitive(String.valueOf(value));
        };
    }

    private static Object toJavaValue(JsonPrimitive value) {
        if (value.isNumber()) {
            return value.getAsNumber();
        }
        return value.isBoolean() ? value.getAsBoolean() : value.getAsString();
    }

    private static Stream<JsonPrimitive> fieldValues(JsonElement root, String param) {
        return ResponseCollections.items(root).stream()
                .flatMap(items -> items.asList().stream())
                .map(item -> fieldValue(item, param))
                .flatMap(Optional::stream);
    }

    private static Optional<JsonPrimitive> fieldValue(JsonElement item, String param) {
        if (item == null || !item.isJsonObject()) {
            return Optional.empty();
        }
        String normalizedParam = WordUtils.normalizeAlphaNumeric(param);
        return item.getAsJsonObject().entrySet().stream()
                .filter(entry -> WordUtils.normalizeAlphaNumeric(entry.getKey()).equals(normalizedParam))
                .map(Map.Entry::getValue)
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsJsonPrimitive)
                .findFirst();
    }

    private void judge(CatsResponse response, FuzzingData data, FilterProbe filter) {
        if (ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
            listener.reportResultError(logger, data, Reason.SERVER_ERROR.value(), "GET filtered by [{}={}] returned {}",
                    filter.param(), filter.displayValue(), response.responseCodeAsString());
            return;
        }
        inspectableItems(response).ifPresentOrElse(items -> reportVerdict(data, filter, verdict(items, filter)),
                () -> listener.reportResultInfo(logger, data, "Filter check inconclusive (status {})", response.responseCodeAsString()));
    }

    static FilterVerdict verdict(JsonArray items, FilterProbe filter) {
        List<JsonPrimitive> values = fieldValues(items, filter.param()).toList();
        List<String> mismatches = values.stream()
                .filter(Predicate.not(value -> sameValue(value, filter.value())))
                .map(JsonPrimitive::getAsString)
                .distinct()
                .limit(MAX_REPORTED_MISMATCHES)
                .toList();
        return new FilterVerdict(values.size(), mismatches);
    }

    static boolean sameValue(JsonPrimitive actual, JsonPrimitive expected) {
        if (actual.isNumber() && expected.isNumber()) {
            return actual.getAsBigDecimal().compareTo(expected.getAsBigDecimal()) == 0;
        }
        return actual.getAsString().trim().equalsIgnoreCase(expected.getAsString().trim());
    }

    private void reportVerdict(FuzzingData data, FilterProbe filter, FilterVerdict verdict) {
        if (verdict.compared() == 0) {
            listener.reportResultInfo(logger, data, "No returned item exposes a field matching [{}]; filter check inconclusive", filter.param());
            return;
        }
        if (verdict.mismatches().isEmpty()) {
            listener.reportResultInfo(logger, data, "Filter [{}={}] honored ({} item(s) compared)",
                    filter.param(), filter.displayValue(), verdict.compared());
            return;
        }
        listener.reportResultWarn(logger, data, Reason.FILTER_PARAMETER_IGNORED.value(),
                "GET filtered by [{}={}] returned items with different values {}",
                filter.param(), filter.displayValue(), verdict.mismatches());
    }

    private static Optional<JsonArray> inspectableItems(CatsResponse response) {
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode()) || response.isBodyTruncated()
                || !JsonUtils.isValidJson(response.getBody())) {
            return Optional.empty();
        }
        return ResponseCollections.items(JsonUtils.parseAsJsonElement(response.getBody()));
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "send a GET collection request filtered by an exact-match query parameter and warn if returned items hold a different value for that field";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
