package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
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
import java.util.Map;
import java.util.Optional;

/**
 * Sends hostile values as elements of arrays whose item schema permits any JSON type.
 */
@Singleton
@FieldFuzzer
public class FreeFormArrayItemsFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FreeFormArrayItemsFieldsFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new free-form array items fuzzer.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public FreeFormArrayItemsFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        this.simpleExecutor = simpleExecutor;
        this.processingArguments = processingArguments;
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
            logger.debug("Skipping fuzzer because no arrays with free-form items were found");
            return;
        }

        List<FreeFormPayloadGenerator.HostileValue> hostileValues = FreeFormPayloadGenerator.generate(
                processingArguments.getLargeStringsSize()).hostileValues();
        for (JsonPayloadTarget target : targets) {
            for (FreeFormPayloadGenerator.HostileValue hostileValue : hostileValues) {
                replaceItem(payload, target, hostileValue.value())
                        .ifPresent(fuzzedPayload -> execute(data, target, hostileValue, fuzzedPayload));
            }
        }
    }

    private void execute(FuzzingData data, JsonPayloadTarget target,
                         FreeFormPayloadGenerator.HostileValue hostileValue, String payload) {
        simpleExecutor.execute(SimpleExecutorContext.builder()
                .fuzzingData(data)
                .fuzzer(this)
                .logger(logger)
                .payload(payload)
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                .scenario("Use %s in free-form array [%s]".formatted(
                        hostileValue.description(), target.displayName()))
                .matchResponseResult(false)
                .matchResponseContentType(false)
                .build());
    }

    private static List<JsonPayloadTarget> findTargets(FuzzingData data, String payload) {
        List<JsonPayloadTarget> targets = new ArrayList<>();
        for (JsonPayloadTarget target : JsonPayloadTarget.candidatesFor(data)) {
            if (isFreeFormArray(target, data.getSchemaMap()) && canMutate(payload, target)) {
                targets.add(target);
            }
        }
        return targets;
    }

    private static boolean isFreeFormArray(JsonPayloadTarget target, Map<String, Schema> schemaMap) {
        Schema<?> schema = target.schema();
        return CatsModelUtils.isArraySchema(schema) && CatsModelUtils.isFreeFormSchema(
                JsonPayloadTarget.resolveSchema(schema.getItems(), schemaMap));
    }

    private static boolean canMutate(String payload, JsonPayloadTarget target) {
        return getTargetArray(payload, target)
                .filter(array -> !array.isEmpty() || target.schema().getMaxItems() == null ||
                        target.schema().getMaxItems() > 0)
                .isPresent();
    }

    private static Optional<String> replaceItem(String payload, JsonPayloadTarget target, JsonElement value) {
        Optional<JsonArray> targetArray = getTargetArray(payload, target);
        if (targetArray.isEmpty()) {
            return Optional.empty();
        }

        JsonArray mutated = targetArray.get().deepCopy();
        if (mutated.isEmpty()) {
            mutated.add(value.deepCopy());
        } else {
            mutated.set(0, value.deepCopy());
        }

        return Optional.of(target.replaceValueIn(payload, mutated));
    }

    private static Optional<JsonArray> getTargetArray(String payload, JsonPayloadTarget target) {
        return target.arrayFrom(payload);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "send hostile values in arrays with unconstrained items and expect 2XX or 4XX responses";
    }

}
