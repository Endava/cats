package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final int DEEP_OBJECT_DEPTH = 32;

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

        List<FreeFormObjectTarget> targets = findTargets(data, payload);
        if (targets.isEmpty()) {
            logger.debug("Skipping fuzzer because no explicitly free-form objects were found");
            return;
        }

        List<FreeFormMutation> mutations = createMutations();
        for (FreeFormObjectTarget target : targets) {
            for (FreeFormMutation mutation : mutations) {
                addProperties(payload, target, mutation).ifPresent(fuzzedPayload -> execute(data, target, mutation, fuzzedPayload));
            }
        }
    }

    private void execute(FuzzingData data, FreeFormObjectTarget target, FreeFormMutation mutation, String payload) {
        simpleExecutor.execute(SimpleExecutorContext.builder()
                .fuzzingData(data)
                .fuzzer(this)
                .logger(logger)
                .payload(payload)
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                .scenario("Add %s to free-form object [%s]".formatted(mutation.description(), target.displayName()))
                .matchResponseResult(false)
                .matchResponseContentType(false)
                .build());
    }

    private List<FreeFormObjectTarget> findTargets(FuzzingData data, String payload) {
        List<FreeFormObjectTarget> targets = new ArrayList<>();
        Schema<?> rootSchema = resolveSchema(data.getReqSchema(), data.getSchemaMap());
        if (isExplicitlyFreeForm(rootSchema) && JsonParser.parseString(payload).isJsonObject()) {
            targets.add(FreeFormObjectTarget.rootTarget());
        }

        Map<String, Schema> propertyTypes = Optional.ofNullable(data.getRequestPropertyTypes()).orElseGet(Map::of);
        for (String field : data.getAllFieldsByHttpMethod()) {
            Schema<?> schema = resolveSchema(propertyTypes.get(field), data.getSchemaMap());
            if (isExplicitlyFreeForm(schema) &&
                    getTargetObject(payload, FreeFormObjectTarget.field(field)).isPresent()) {
                targets.add(FreeFormObjectTarget.field(field));
            }
        }
        return targets;
    }

    private static Schema<?> resolveSchema(Schema<?> schema, Map<String, Schema> schemaMap) {
        if (schema == null || schema.get$ref() == null || schemaMap == null) {
            return schema;
        }
        String schemaName = CatsModelUtils.getSimpleRef(schema.get$ref());
        return schemaName == null ? schema : schemaMap.getOrDefault(schemaName, schema);
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
        return Boolean.TRUE.equals(additionalPropertiesSchema.getBooleanSchemaValue()) ||
                (additionalPropertiesSchema.getBooleanSchemaValue() == null &&
                        !CatsModelUtils.isNotEmptySchema(additionalPropertiesSchema));
    }

    private Optional<String> addProperties(String payload, FreeFormObjectTarget target, FreeFormMutation mutation) {
        Optional<JsonObject> targetObject = getTargetObject(payload, target);
        if (targetObject.isEmpty()) {
            return Optional.empty();
        }

        JsonObject mutated = targetObject.get().deepCopy();
        mutation.properties().forEach((key, value) -> mutated.add(key, value.deepCopy()));
        if (target.root()) {
            return Optional.of(mutated.toString());
        }
        return Optional.of(CatsUtil.justReplaceField(payload, target.path(), mutated.toString()).json());
    }

    private static Optional<JsonObject> getTargetObject(String payload, FreeFormObjectTarget target) {
        if (target.root()) {
            JsonElement root = JsonParser.parseString(payload);
            return root.isJsonObject() ? Optional.of(root.getAsJsonObject()) : Optional.empty();
        }

        Object value = JsonUtils.getVariableFromJson(payload, target.path());
        String serialized = JsonUtils.serialize(value);
        if (serialized == null) {
            return Optional.empty();
        }
        JsonElement element = JsonParser.parseString(serialized);
        return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
    }

    private List<FreeFormMutation> createMutations() {
        int largeStringRepetitions = Math.max(1, processingArguments.getLargeStringsSize() / 4);
        String largeString = StringGenerator.generateLargeString(largeStringRepetitions);
        String largeUnicode = String.valueOf(FuzzingStrategy.getLargeValuesStrategy(
                processingArguments.getLargeStringsSize()).getFirst().getData());
        String zeroWidthKey = "cats" + String.join("", UnicodeGenerator.getZwCharsSmallListFields()) + "key";
        String controlChars = String.join("", UnicodeGenerator.getControlCharsFields());

        List<FreeFormMutation> mutations = new ArrayList<>();
        mutations.add(FreeFormMutation.single("an empty property name", "", new JsonPrimitive(SIMPLE_VALUE)));
        mutations.add(FreeFormMutation.single("an invisible property name", zeroWidthKey, new JsonPrimitive(SIMPLE_VALUE)));
        mutations.add(FreeFormMutation.single("a control-character property name", "cats" + controlChars, new JsonPrimitive(SIMPLE_VALUE)));
        mutations.add(FreeFormMutation.single("an extremely long property name", largeString, new JsonPrimitive(SIMPLE_VALUE)));
        mutations.add(FreeFormMutation.single("a path-like property name", "../[cats]/~0~1", new JsonPrimitive(SIMPLE_VALUE)));
        mutations.add(new FreeFormMutation("reserved and prototype-pollution property names", reservedProperties()));
        mutations.add(FreeFormMutation.single("an extremely long string value", VALUE_KEY, new JsonPrimitive(largeString)));
        mutations.add(FreeFormMutation.single("an extremely large Unicode value", VALUE_KEY, new JsonPrimitive(largeUnicode)));
        mutations.add(FreeFormMutation.single("control characters as a value", VALUE_KEY, new JsonPrimitive(controlChars)));
        mutations.add(FreeFormMutation.single("an extreme numeric value", VALUE_KEY, new JsonPrimitive(NumberGenerator.MOST_POSITIVE_INTEGER)));
        mutations.add(FreeFormMutation.single("a null value", VALUE_KEY, JsonNull.INSTANCE));
        mutations.add(FreeFormMutation.single("a deeply nested object", VALUE_KEY, deeplyNestedObject()));
        mutations.add(FreeFormMutation.single("a heterogeneous array", VALUE_KEY, heterogeneousArray()));
        return mutations;
    }

    private static Map<String, JsonElement> reservedProperties() {
        Map<String, JsonElement> properties = new LinkedHashMap<>();
        properties.put("__proto__", new JsonPrimitive(SIMPLE_VALUE));
        properties.put("constructor", new JsonPrimitive(SIMPLE_VALUE));
        properties.put("prototype", new JsonPrimitive(SIMPLE_VALUE));
        properties.put("$where", new JsonPrimitive("sleep(5000)"));
        return properties;
    }

    private static JsonObject deeplyNestedObject() {
        JsonObject result = new JsonObject();
        JsonObject current = result;
        for (int i = 0; i < DEEP_OBJECT_DEPTH; i++) {
            JsonObject child = new JsonObject();
            current.add("catsNested" + i, child);
            current = child;
        }
        current.addProperty("value", UnicodeGenerator.getZalgoText());
        return result;
    }

    private static JsonArray heterogeneousArray() {
        JsonArray array = new JsonArray();
        array.add(JsonNull.INSTANCE);
        array.add(NumberGenerator.MOST_NEGATIVE_INTEGER);
        array.add(UnicodeGenerator.getBadPayload());
        array.add(deeplyNestedObject());
        return array;
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

    private record FreeFormObjectTarget(String path, boolean root) {
        private static FreeFormObjectTarget rootTarget() {
            return new FreeFormObjectTarget("$", true);
        }

        private static FreeFormObjectTarget field(String path) {
            return new FreeFormObjectTarget(path, false);
        }

        private String displayName() {
            return root ? "root" : path;
        }
    }

    private record FreeFormMutation(String description, Map<String, JsonElement> properties) {
        private static FreeFormMutation single(String description, String key, JsonElement value) {
            return new FreeFormMutation(description, Map.of(key, value));
        }
    }
}
