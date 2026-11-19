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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Iterates through each JSON field and sends a payload where that
 * field appears twice inside its owning object, e.g.
 * {"id":1,"id":"catsFuzzyDup"}.
 * Detects first-wins / last-wins ambiguities per RFC 8259.
 */
@FieldFuzzer
@Singleton
public class DuplicateKeysFieldsFuzzer implements Fuzzer {

    private static final PrettyLogger logger = PrettyLoggerFactory.getLogger(DuplicateKeysFieldsFuzzer.class);

    // Configuration constants
    private static final String DUPLICATE_VALUE = "catsFuzzyDup";
    private static final int MAX_FIELD_DEPTH = 5;
    private static final int MAX_MUTATIONS_PER_REQUEST = 100;

    private final SimpleExecutor simpleExecutor;

    public DuplicateKeysFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.debug("Skipping fuzzer for path {} - empty payload", data.getPath());
            return;
        }

        executeFieldMutations(data);
    }

    private void executeFieldMutations(FuzzingData data) {
        data.getAllFieldsByHttpMethod().stream()
                .filter(field -> !shouldSkipField(field, data.getPayload()))
                .limit(MAX_MUTATIONS_PER_REQUEST)
                .forEach(field -> executeFieldDuplication(data, field));
    }

    private boolean shouldSkipField(String field, String payload) {
        int fieldDepth = calculateFieldDepth(field);
        if (fieldDepth > MAX_FIELD_DEPTH) {
            logger.debug("Skipping field {} - depth {} exceeds limit", field, fieldDepth);
            return true;
        }

        if (!JsonUtils.isFieldInJson(payload, field)) {
            logger.debug("Skipping field {} - not found in payload", field);
            return true;
        }

        return false;
    }

    private int calculateFieldDepth(String field) {
        return field.split("#").length;
    }

    private void executeFieldDuplication(FuzzingData data, String field) {
        Optional<String> duplicatedPayload = createDuplicatedPayload(data.getPayload(), field);

        if (duplicatedPayload.isEmpty()) {
            logger.debug("Failed to create duplicate key for field {}", field);
            return;
        }

        runDuplicationTestCase(data, field, duplicatedPayload.get(),
                "Duplicate key [" + field + "] in parent object with second value [" + DUPLICATE_VALUE + "]");
        createEscapedCollisionPayload(data.getPayload(), field)
                .ifPresent(collision -> runDuplicationTestCase(data, field, collision,
                        "Duplicate key [" + field + "] using Unicode escape and surrogate-truncation key collisions"));
    }

    private Optional<String> createDuplicatedPayload(String originalPayload, String fieldPath) {
        return renderPayloadVariant(originalPayload, fieldPath, false);
    }

    Optional<String> createEscapedCollisionPayload(String originalPayload, String fieldPath) {
        return renderPayloadVariant(originalPayload, fieldPath, true);
    }

    private Optional<String> renderPayloadVariant(String originalPayload, String fieldPath, boolean collisionKeys) {
        try {
            JsonElement root = JsonParser.parseString(originalPayload);
            DuplicationStep step = new DuplicationStep(List.of(fieldPath.split("#")), 0, collisionKeys);
            String duplicatedJson = renderJsonWithDuplicateKey(root, step);
            return duplicatedJson.equals(originalPayload) ? Optional.empty() : Optional.of(duplicatedJson);
        } catch (Exception e) {
            logger.debug("Error creating duplicated payload for field {}: {}", fieldPath, e.getMessage());
            return Optional.empty();
        }
    }

    private void runDuplicationTestCase(FuzzingData data, String field, String duplicatedPayload, String scenario) {
        simpleExecutor.execute(SimpleExecutorContext.builder()
                .logger(logger)
                .fuzzer(this)
                .fuzzingData(data)
                .payload(duplicatedPayload)
                .mutationTarget(RequestTarget.body(field))
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .scenario(scenario)
                .replaceRefData(false)
                .validJson(false)
                .build());
    }

    private record DuplicationStep(List<String> path, int depth, boolean collisionKeys) {
        boolean isComplete() {
            return depth >= path.size();
        }

        String currentKey() {
            return path.get(depth);
        }

        boolean atTargetLevel() {
            return depth == path.size() - 1;
        }

        DuplicationStep next() {
            return new DuplicationStep(path, depth + 1, collisionKeys);
        }
    }

    private String renderJsonWithDuplicateKey(JsonElement element, DuplicationStep step) {
        if (step.isComplete()) {
            return element.toString();
        }
        if (element.isJsonObject()) {
            return renderObjectWithDuplicateKey(element.getAsJsonObject(), step);
        }
        if (element.isJsonArray()) {
            return renderArrayWithDuplicateKey(element, step);
        }
        return element.toString();
    }

    private String renderObjectWithDuplicateKey(JsonObject obj, DuplicationStep step) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            appendObjectEntry(joiner, entry, step);
        }
        return joiner.toString();
    }

    private void appendObjectEntry(StringJoiner joiner, Map.Entry<String, JsonElement> entry, DuplicationStep step) {
        String key = entry.getKey();
        boolean matchesKey = key.equals(step.currentKey());
        String renderedValue = matchesKey && !step.atTargetLevel()
                ? renderJsonWithDuplicateKey(entry.getValue(), step.next())
                : entry.getValue().toString();
        joiner.add(formatJsonMember(key, renderedValue));
        if (matchesKey && step.atTargetLevel()) {
            appendDuplicateMembers(joiner, key, step.collisionKeys());
        }
    }

    private void appendDuplicateMembers(StringJoiner joiner, String key, boolean collisionKeys) {
        String duplicateVal = JsonUtils.GSON.toJson(DUPLICATE_VALUE);
        if (!collisionKeys) {
            joiner.add(formatJsonMember(key, duplicateVal));
            return;
        }
        joiner.add("\"%s\":%s".formatted(escapeFirstCharacterAsUnicode(key), duplicateVal));
        joiner.add("\"%s\\uD800\":%s".formatted(JsonUtils.escape(key), duplicateVal));
    }

    static String escapeFirstCharacterAsUnicode(String key) {
        if (key.isEmpty()) {
            return key;
        }
        return "\\u%04x%s".formatted((int) key.charAt(0), JsonUtils.escape(key.substring(1)));
    }

    private String renderArrayWithDuplicateKey(JsonElement arrayElement, DuplicationStep step) {
        StringJoiner arrayJoiner = new StringJoiner(",", "[", "]");
        for (JsonElement element : arrayElement.getAsJsonArray()) {
            arrayJoiner.add(renderJsonWithDuplicateKey(element, step));
        }
        return arrayJoiner.toString();
    }

    private String formatJsonMember(String key, String valueJson) {
        return String.format("\"%s\":%s", JsonUtils.escape(key), valueJson);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @Override
    public String description() {
        return "Duplicates each JSON key to detect first-wins/last-wins parsing ambiguities per RFC 8259";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
