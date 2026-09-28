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
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads back a resource created and updated in this run, comparing only present, writable primitive fields.
 * Mismatches are warnings because another fuzzer may have changed the resource between the update and the read.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class UpdateReadConsistencyFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(UpdateReadConsistencyFuzzer.class);
    private final RuntimeResourcePool pool;
    private final ApiArguments arguments;
    private final FilterArguments filterArguments;
    private final SimpleExecutor executor;
    private final TestCaseListener listener;

    /** Creates a read-back check using the successful exchanges retained for this run. */
    public UpdateReadConsistencyFuzzer(RuntimeResourcePool pool, ApiArguments arguments, FilterArguments filterArguments,
                                       SimpleExecutor executor, TestCaseListener listener) {
        this.pool = pool;
        this.arguments = arguments;
        this.filterArguments = filterArguments;
        this.executor = executor;
        this.listener = listener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!hasInScopeItemReadAndWritableSchema(data)) {
            logger.skip("Update/read check needs an in-scope item GET and request schema on {} {}", data.getMethod(), data.getPath());
            return;
        }
        Optional<ObservedUpdate> update = latestCreatedResourceUpdate(data);
        if (update.isEmpty()) {
            logger.skip("No successful {} to a resource created by POST 201 Location was observed for {}",
                    data.getMethod(), data.getPath());
            return;
        }
        ObservedUpdate observed = update.get();
        executor.execute(SimpleExecutorContext.builder().fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.GET).path(observed.path()).payload("{}")
                .replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Read a resource created and updated in this run, checking its persisted fields")
                .responseProcessor((response, context) -> checkResponse(response, context, observed.body()))
                .build());
    }

    private boolean hasInScopeItemReadAndWritableSchema(FuzzingData data) {
        return (data.getMethod() == HttpMethod.PUT || data.getMethod() == HttpMethod.PATCH) &&
                data.getPathItem() != null && data.getPathItem().getGet() != null && data.getReqSchema() != null &&
                filterArguments.isHttpMethodSupplied(HttpMethod.GET);
    }

    /** Selects a successful update only if a matching POST 201 Location proves the item was created in this run. */
    private Optional<ObservedUpdate> latestCreatedResourceUpdate(FuzzingData data) {
        return pool.successfulExchanges(data.getPath(), data.getMethod()).reversed().stream()
                .filter(update -> JsonUtils.isValidJson(update.requestBody()))
                .map(update -> ObservedHttpTarget.ownedPath(pool, arguments, data.getPath(), update.url())
                        .map(path -> new ObservedUpdate(path, update.requestBody())))
                .flatMap(Optional::stream).findFirst();
    }

    private void checkResponse(CatsResponse response, FuzzingData data, String requestBody) {
        if (response.getResponseCode() == 404 || response.getResponseCode() == 410) {
            listener.reportResultInfo(logger, data, "Updated resource no longer available for read (status {})", response.responseCodeAsString());
            return;
        }
        if (!isCompleteSuccessfulJson(response)) {
            listener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX, false);
            return;
        }
        JsonElement before = JsonUtils.parseAsJsonElement(requestBody);
        JsonElement after = JsonUtils.parseAsJsonElement(response.getBody());
        if (!before.isJsonObject() || !after.isJsonObject()) {
            listener.reportResultInfo(logger, data, "Update/read comparison not possible for a non-object response");
            return;
        }
        reportComparedFields(data, before.getAsJsonObject(), after.getAsJsonObject());
    }

    private boolean isCompleteSuccessfulJson(CatsResponse response) {
        return response.getResponseCode() >= 200 && response.getResponseCode() < 300 &&
                !response.isBodyTruncated() && JsonUtils.isValidJson(response.getBody());
    }

    /** Compares only fields that may be both written and returned; absent fields prove nothing about persistence. */
    private void reportComparedFields(FuzzingData data, JsonObject sent, JsonObject returned) {
        Map<String, Schema> properties = requestProperties(data);
        List<String> mismatched = new ArrayList<>();
        int compared = 0;
        for (Map.Entry<String, Schema> entry : properties.entrySet()) {
            String name = entry.getKey();
            if (!isComparableWritableField(entry, sent, returned)) {
                continue;
            }
            compared++;
            if (!sent.get(name).equals(returned.get(name))) {
                mismatched.add(name);
            }
        }
        if (!mismatched.isEmpty()) {
            listener.reportResultWarn(logger, data, "Possible update/read inconsistency",
                    "Fields sent in a successful update differ from a subsequent read: {}. Another fuzzer may have modified the resource in between",
                    mismatched);
        } else if (compared > 0) {
            listener.reportResultInfo(logger, data, "Compared {} fields from a successful update with a later read", compared);
        } else {
            listener.reportResultInfo(logger, data, "No comparable writable fields were present in the read response");
        }
    }

    /** Resolves a direct request-schema reference before choosing comparable fields. */
    private Map<String, Schema> requestProperties(FuzzingData data) {
        Schema<?> schema = data.getReqSchema();
        if (schema.get$ref() != null && data.getSchemaMap() != null) {
            schema = data.getSchemaMap().get(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
        }
        return schema == null ? Map.of() : Optional.ofNullable(schema.getProperties()).orElse(Map.of());
    }

    /** Ignores readOnly/writeOnly, missing, and non-primitive fields rather than assuming they were persisted. */
    private boolean isComparableWritableField(Map.Entry<String, Schema> property, JsonObject sent, JsonObject returned) {
        String name = property.getKey();
        return property.getValue() != null && !Boolean.TRUE.equals(property.getValue().getReadOnly()) &&
                !Boolean.TRUE.equals(property.getValue().getWriteOnly()) && sent.has(name) && returned.has(name) &&
                sent.get(name).isJsonPrimitive() && returned.get(name).isJsonPrimitive();
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "read back a created resource after a successful update and warn on differences in comparable writable fields";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }

    private record ObservedUpdate(String path, String body) {
    }
}
