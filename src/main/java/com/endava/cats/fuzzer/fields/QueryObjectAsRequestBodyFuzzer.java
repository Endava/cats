package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fuzzer for non-body HTTP operations (such as {@code GET}) that declare a complex object schema
 * in query parameters (common when {@code @GetMapping} is combined with {@code @RequestBody} in Spring).
 * Sends the nested query object as a JSON request body (full, required-only, and empty {@code {}}).
 */
@Singleton
@FieldFuzzer
public class QueryObjectAsRequestBodyFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new QueryObjectAsRequestBodyFuzzer instance.
     *
     * @param simpleExecutor      the executor
     * @param processingArguments the processing arguments
     */
    public QueryObjectAsRequestBodyFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        this.simpleExecutor = simpleExecutor;
        this.processingArguments = processingArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (HttpMethod.requiresBody(data.getMethod()) || JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Fuzzer only runs for non-body methods with complex query object parameters");
            return;
        }
        JsonElement root = JsonUtils.parseAsJsonElement(data.getPayload());
        if (root == null || !root.isJsonObject()) {
            logger.skip("Payload is not a JSON object");
            return;
        }
        Set<String> queryParams = data.getQueryParams() == null ? Set.of() : data.getQueryParams();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            if (queryParams.contains(entry.getKey()) && entry.getValue() != null && entry.getValue().isJsonObject()) {
                executeForQueryObject(data, entry.getKey(), entry.getValue().getAsJsonObject());
            }
        }
    }

    private void executeForQueryObject(FuzzingData data, String paramName, JsonObject objectValue) {
        String fullJson = objectValue.toString();
        String prefix = paramName + "#";
        List<String> nestedRequired = data.getAllRequiredFields() == null ? List.of()
                : data.getAllRequiredFields().stream()
                .filter(field -> field.startsWith(prefix))
                .map(field -> field.substring(prefix.length()))
                .toList();
        String requiredOnlyJson = RequiredOnlyPayloadGenerator.generate(fullJson, nestedRequired);
        Set<String> bodies = new LinkedHashSet<>();
        bodies.add(requiredOnlyJson);
        bodies.add(fullJson);
        bodies.add("{}");
        for (String body : bodies) {
            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                            .fuzzingData(data)
                            .logger(logger)
                            .scenario("Send query object parameter [%s] as JSON request body on %s"
                                    .formatted(paramName, data.getMethod()))
                            .fuzzer(this)
                            .payload(body)
                            .mutationTarget(RequestTarget.requestBody())
                            .build());
        }
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "send complex query object parameters as JSON request bodies on non-body HTTP methods";
    }
}
