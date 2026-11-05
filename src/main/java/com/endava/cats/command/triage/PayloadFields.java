package com.endava.cats.command.triage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only navigation of request payloads using triage dotted paths ({@code a.b}, {@code items[*].qty}),
 * where {@code [*]} addresses the first array element.
 */
final class PayloadFields {
    private static final int MAX_DEPTH = 3;
    private static final String WILDCARD = "[*]";

    /**
     * A primitive leaf of the payload.
     */
    record PrimitiveField(String path, String key, JsonPrimitive value) {
    }

    private PayloadFields() {
    }

    static Optional<JsonElement> valueAt(JsonObject root, String dottedPath) {
        Optional<JsonElement> current = Optional.of(root);
        for (String segment : dottedPath.split("\\.", -1)) {
            current = current.flatMap(element -> step(element, segment));
        }
        return current;
    }

    static boolean isBlank(Optional<JsonElement> value) {
        return value.map(PayloadFields::isBlankElement).orElse(true);
    }

    static Optional<String> findFieldPath(JsonObject root, String normalizedName) {
        return collectPaths(root).stream()
                .filter(path -> FieldNames.normalize(FieldNames.leaf(path)).equals(normalizedName))
                .findFirst();
    }

    static List<PrimitiveField> primitiveFields(JsonObject root) {
        List<PrimitiveField> fields = new ArrayList<>();
        visit(root, "", 0, (path, key, value) -> {
            if (value.isJsonPrimitive()) {
                fields.add(new PrimitiveField(path, key, value.getAsJsonPrimitive()));
            }
        });
        return List.copyOf(fields);
    }

    private static List<String> collectPaths(JsonObject root) {
        List<String> paths = new ArrayList<>();
        visit(root, "", 0, (path, _, _) -> paths.add(path));
        return List.copyOf(paths);
    }

    private static Optional<JsonElement> step(JsonElement element, String segment) {
        boolean wildcard = segment.endsWith(WILDCARD);
        String name = wildcard ? segment.substring(0, segment.length() - WILDCARD.length()) : segment;
        Optional<JsonElement> child = element.isJsonObject()
                ? Optional.ofNullable(element.getAsJsonObject().get(name))
                : Optional.empty();
        return wildcard ? child.flatMap(PayloadFields::firstArrayElement) : child;
    }

    private static Optional<JsonElement> firstArrayElement(JsonElement element) {
        return element.isJsonArray() && !element.getAsJsonArray().isEmpty()
                ? Optional.of(element.getAsJsonArray().get(0))
                : Optional.empty();
    }

    private static boolean isBlankElement(JsonElement element) {
        if (element.isJsonNull()) {
            return true;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString().isBlank();
        }
        return element.isJsonArray() ? element.getAsJsonArray().isEmpty() : element.getAsJsonObject().isEmpty();
    }

    @FunctionalInterface
    private interface FieldVisitor {
        void accept(String path, String key, JsonElement value);
    }

    private static void visit(JsonObject object, String prefix, int depth, FieldVisitor visitor) {
        if (depth > MAX_DEPTH) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            visitor.accept(path, entry.getKey(), entry.getValue());
            descend(entry.getValue(), path, depth, visitor);
        }
    }

    private static void descend(JsonElement value, String path, int depth, FieldVisitor visitor) {
        if (value.isJsonObject()) {
            visit(value.getAsJsonObject(), path, depth + 1, visitor);
        } else {
            firstArrayElement(value)
                    .filter(JsonElement::isJsonObject)
                    .ifPresent(first -> visit(first.getAsJsonObject(), path + WILDCARD, depth + 1, visitor));
        }
    }
}
