package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.StreamSupport;

/**
 * Duplicates the target JSON key within its owning object with a conflicting value
 * to trigger RFC 8259 first-key-wins vs. last-key-wins parser differentials.
 */
@Singleton
public class DuplicateJsonFieldKeyMutator implements BodyMutator {
    private static final List<String> CONFLICTING_JSON_LITERALS = List.of(
            "null", "\"\"", "-1", "0", "false", "true", "[]", "{}");

    @Override
    public String mutate(String inputJson, String selectedField) {
        if (JsonUtils.isEmptyPayload(inputJson) || selectedField == null || selectedField.isBlank()) {
            return inputJson;
        }
        try {
            JsonElement root = JsonUtils.JSON_STRICT_PARSER.fromJson(inputJson, JsonElement.class);
            if (root == null || (!root.isJsonObject() && !root.isJsonArray())) {
                return inputJson;
            }
            return renderWithDuplicateKey(root, new RenderStep(List.of(selectedField.split("#")), 0));
        } catch (Exception _) {
            return inputJson;
        }
    }

    private record RenderStep(List<String> path, int depth) {
        boolean isComplete() {
            return depth >= path.size();
        }

        String targetKey() {
            return path.get(depth);
        }

        boolean atLeaf() {
            return depth == path.size() - 1;
        }

        RenderStep next() {
            return new RenderStep(path, depth + 1);
        }
    }

    private static String renderWithDuplicateKey(JsonElement element, RenderStep step) {
        if (step.isComplete()) {
            return element.toString();
        }
        if (element.isJsonObject()) {
            return renderObject(element.getAsJsonObject(), step);
        }
        if (element.isJsonArray()) {
            return StreamSupport.stream(element.getAsJsonArray().spliterator(), false)
                    .map(child -> renderWithDuplicateKey(child, step))
                    .reduce((a, b) -> a + "," + b)
                    .map(joined -> "[" + joined + "]")
                    .orElse("[]");
        }
        return element.toString();
    }

    private static String renderObject(JsonObject obj, RenderStep step) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            appendEntry(joiner, entry, step);
        }
        return joiner.toString();
    }

    private static void appendEntry(StringJoiner joiner, Map.Entry<String, JsonElement> entry, RenderStep step) {
        String key = entry.getKey();
        boolean matchesTarget = key.equals(step.targetKey());
        String renderedValue = matchesTarget && !step.atLeaf()
                ? renderWithDuplicateKey(entry.getValue(), step.next())
                : entry.getValue().toString();
        joiner.add(formatMember(key, renderedValue));

        if (matchesTarget && step.atLeaf()) {
            String conflictingLiteral = CONFLICTING_JSON_LITERALS.get(
                    CatsRandom.instance().nextInt(CONFLICTING_JSON_LITERALS.size()));
            joiner.add(formatMember(key, conflictingLiteral));
        }
    }

    private static String formatMember(String key, String rawJsonValue) {
        return "\"%s\":%s".formatted(JsonUtils.escape(key), rawJsonValue);
    }

    @Override
    public String description() {
        return "duplicate target JSON key in its parent object with a conflicting second value";
    }
}
