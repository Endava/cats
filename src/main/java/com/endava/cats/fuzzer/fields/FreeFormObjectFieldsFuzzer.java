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
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adds hostile but valid JSON properties to objects explicitly allowing unrestricted additional properties.
 */
@Singleton
@FieldFuzzer
public class FreeFormObjectFieldsFuzzer implements Fuzzer {
    private static final String VALUE_KEY = "catsFreeFormValue";
    private static final String SIMPLE_VALUE = "catsFuzzyValue";

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FreeFormObjectFieldsFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new FreeFormObjectFieldsFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public FreeFormObjectFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
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
            logger.debug("Skipping fuzzer because no explicitly free-form objects were found");
            return;
        }

        List<FreeFormMutation> mutations = createMutations();
        for (JsonPayloadTarget target : targets) {
            for (FreeFormMutation mutation : mutations) {
                addProperties(payload, target, mutation).ifPresent(fuzzedPayload -> execute(data, target, mutation, fuzzedPayload));
            }
        }
    }

    private void execute(FuzzingData data, JsonPayloadTarget target, FreeFormMutation mutation, String payload) {
        simpleExecutor.execute(SimpleExecutorContext.builder()
                .fuzzingData(data)
                .fuzzer(this)
                .logger(logger)
                .payload(payload)
                .mutationTarget(target.root() ? RequestTarget.requestBody() : RequestTarget.body(target.path()))
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                .scenario("Add %s to free-form object [%s]".formatted(mutation.description(), target.displayName()))
                .matchResponseResult(false)
                .matchResponseContentType(false)
                .build());
    }

    private List<JsonPayloadTarget> findTargets(FuzzingData data, String payload) {
        List<JsonPayloadTarget> targets = new ArrayList<>();
        for (JsonPayloadTarget target : JsonPayloadTarget.candidatesFor(data)) {
            if (isExplicitlyFreeForm(target.schema()) && target.objectFrom(payload).isPresent()) {
                targets.add(target);
            }
        }
        return targets;
    }

    private static boolean isExplicitlyFreeForm(Schema<?> schema) {
        if (schema == null) {
            return false;
        }

        Object additionalProperties = schema.getAdditionalProperties();
        if (Boolean.TRUE.equals(additionalProperties)) {
            return true;
        }
        if (!(additionalProperties instanceof Schema<?> additionalPropertiesSchema)) {
            return false;
        }
        return CatsModelUtils.isFreeFormSchema(additionalPropertiesSchema);
    }

    private Optional<String> addProperties(String payload, JsonPayloadTarget target, FreeFormMutation mutation) {
        Optional<JsonObject> targetObject = target.objectFrom(payload);
        if (targetObject.isEmpty()) {
            return Optional.empty();
        }

        JsonObject mutated = targetObject.get().deepCopy();
        mutation.properties().forEach((key, value) -> mutated.add(key, value.deepCopy()));
        return Optional.of(target.replaceValueIn(payload, mutated));
    }

    private List<FreeFormMutation> createMutations() {
        FreeFormPayloadGenerator.PayloadSet payloads = FreeFormPayloadGenerator.generate(
                processingArguments.getLargeStringsSize());

        List<FreeFormMutation> mutations = new ArrayList<>();
        mutations.add(FreeFormMutation.single("an empty property name", "", new JsonPrimitive(SIMPLE_VALUE)));
        mutations.add(FreeFormMutation.single("an invisible property name", payloads.zeroWidthKey(),
                new JsonPrimitive(SIMPLE_VALUE)));
        mutations.add(FreeFormMutation.single("a control-character property name", "cats" + payloads.controlChars(),
                new JsonPrimitive(SIMPLE_VALUE)));
        mutations.add(FreeFormMutation.single("an extremely long property name", payloads.largeString(),
                new JsonPrimitive(SIMPLE_VALUE)));
        mutations.add(FreeFormMutation.single("a path-like property name", "../[cats]/~0~1", new JsonPrimitive(SIMPLE_VALUE)));
        mutations.add(new FreeFormMutation("reserved and prototype-pollution property names",
                payloads.reservedProperties()));
        payloads.hostileValues().forEach(value -> mutations.add(
                FreeFormMutation.single(value.description(), VALUE_KEY, value.value())));
        return mutations;
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
        return "add hostile keys and values to objects with unrestricted additionalProperties and expect 2XX or 4XX responses";
    }

    private record FreeFormMutation(String description, Map<String, JsonElement> properties) {
        private static FreeFormMutation single(String description, String key, JsonElement value) {
            return new FreeFormMutation(description, Map.of(key, value));
        }
    }
}
