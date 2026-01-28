package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
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

        runDuplicationTestCase(data, field, duplicatedPayload.get());
    }

    private Optional<String> createDuplicatedPayload(String originalPayload, String fieldPath) {
        try {
            JsonElement root = JsonParser.parseString(originalPayload);
            String[] pathSegments = fieldPath.split("#");
            String duplicatedJson = renderJsonWithDuplicateKey(root, pathSegments, 0);

            return duplicatedJson.equals(originalPayload)
                    ? Optional.empty()
                    : Optional.of(duplicatedJson);
        } catch (Exception e) {
            logger.debug("Error creating duplicated payload for field {}: {}", fieldPath, e.getMessage());
            return Optional.empty();
        }
    }

    private void runDuplicationTestCase(FuzzingData data, String field, String duplicatedPayload) {
        simpleExecutor.execute(SimpleExecutorContext.builder()
                .logger(logger)
                .fuzzer(this)
                .fuzzingData(data)
                .payload(duplicatedPayload)
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .scenario("Duplicate key [" + field + "] in parent object with second value [" + DUPLICATE_VALUE + "]")
                .replaceRefData(false)
                .validJson(false)
                .build());
    }


    /**
     * Recursively renders JSON while duplicating the target key when the path matches.
     */
    private String renderJsonWithDuplicateKey(JsonElement element, String[] pathSegments, int currentDepth) {
        if (currentDepth >= pathSegments.length) {
            return element.toString();
        }

        if (element.isJsonObject()) {
            return renderObjectWithDuplicateKey(element.getAsJsonObject(), pathSegments, currentDepth);
        }

        if (element.isJsonArray()) {
            return renderArrayWithDuplicateKey(element, pathSegments, currentDepth);
        }

        return element.toString();
    }

    private String renderObjectWithDuplicateKey(JsonObject obj, String[] path, int depth) {
        String currentKey = path[depth];
        boolean atTargetLevel = depth == path.length - 1;

        StringJoiner joiner = new StringJoiner(",", "{", "}");

        for (Map.Entry<String, JsonElement> en : obj.entrySet()) {
            String key = en.getKey();
            JsonElement v = en.getValue();

            /* normal member (recursing deeper when on the path) */
            if (key.equals(currentKey)) {
                String processed = atTargetLevel
                        ? v.toString()
                        : renderJsonWithDuplicateKey(v, path, depth + 1);
                joiner.add(formatJsonMember(key, processed));

                /* add the duplicate right after the original */
                if (atTargetLevel) {
                    joiner.add(formatJsonMember(key,
                            JsonUtils.GSON.toJson(DUPLICATE_VALUE)));
                }
            } else {
                joiner.add(formatJsonMember(key, v.toString()));
            }
        }
        return joiner.toString();
    }

    private String renderArrayWithDuplicateKey(JsonElement arrayElement, String[] pathSegments, int currentDepth) {
        StringBuilder arrayBuilder = new StringBuilder("[");
        boolean firstElement = true;

        for (JsonElement element : arrayElement.getAsJsonArray()) {
            if (!firstElement) {
                arrayBuilder.append(",");
            }
            firstElement = false;
            arrayBuilder.append(renderJsonWithDuplicateKey(element, pathSegments, currentDepth));
        }

        arrayBuilder.append("]");
        return arrayBuilder.toString();
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