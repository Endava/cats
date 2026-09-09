package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Duplicates an item in arrays declared with {@code uniqueItems: true}.
 */
@Singleton
@FieldFuzzer
public class DuplicateItemsInUniqueArraysFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(DuplicateItemsInUniqueArraysFieldsFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new DuplicateItemsInUniqueArraysFieldsFuzzer instance.
     *
     * @param simpleExecutor the executor used to run each array mutation
     */
    public DuplicateItemsInUniqueArraysFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        String payload = data.getPayload();
        if (!JsonUtils.isValidJson(payload)) {
            logger.debug("Skipping fuzzer because payload is not valid JSON");
            return;
        }

        List<JsonPayloadTarget> targets = findTargets(data, payload);
        if (targets.isEmpty()) {
            logger.debug("Skipping fuzzer because no mutable arrays with uniqueItems enabled were found");
            return;
        }

        for (JsonPayloadTarget target : targets) {
            duplicateItem(payload, target).ifPresent(fuzzedPayload -> simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .fuzzingData(data)
                            .fuzzer(this)
                            .logger(logger)
                            .payload(fuzzedPayload)
                            .mutationTarget(target.root() ? RequestTarget.requestBody() : RequestTarget.body(target.path()))
                            .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                            .replaceRefData(false)
                            .scenario("Duplicate an item in array [%s] declared with uniqueItems"
                                    .formatted(target.displayName()))
                            .build()));
        }
    }

    private static List<JsonPayloadTarget> findTargets(FuzzingData data, String payload) {
        List<JsonPayloadTarget> targets = new ArrayList<>();
        for (JsonPayloadTarget target : JsonPayloadTarget.candidatesFor(data)) {
            if (canDuplicateItem(payload, target)) {
                targets.add(target);
            }
        }
        return targets;
    }

    private static boolean canDuplicateItem(String payload, JsonPayloadTarget target) {
        Schema<?> schema = target.schema();
        if (schema == null || !Boolean.TRUE.equals(schema.getUniqueItems())) {
            return false;
        }

        Optional<JsonArray> array = getArray(payload, target);
        if (array.isEmpty() || array.get().isEmpty()) {
            return false;
        }

        return array.get().size() > 1 || schema.getMaxItems() == null || schema.getMaxItems() > 1;
    }

    private static Optional<String> duplicateItem(String payload, JsonPayloadTarget target) {
        return getArray(payload, target).map(original -> {
            JsonArray result = original.deepCopy();
            JsonElement duplicate = original.get(0).deepCopy();
            if (result.size() == 1) {
                result.add(duplicate);
            } else {
                result.set(result.size() - 1, duplicate);
            }
            return target.replaceValueIn(payload, result);
        });
    }

    private static Optional<JsonArray> getArray(String payload, JsonPayloadTarget target) {
        return target.arrayFrom(payload);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.GET, HttpMethod.DELETE);
    }

    @Override
    public String description() {
        return "iterate through each array with uniqueItems enabled and replace it with an array containing a duplicate item";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
