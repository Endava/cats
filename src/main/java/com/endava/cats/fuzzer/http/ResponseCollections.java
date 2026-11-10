package com.endava.cats.fuzzer.http;

import com.endava.cats.util.WordUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Locates the item array of a JSON collection response, supporting bare arrays and common envelope shapes.
 */
final class ResponseCollections {
    private static final Set<String> ENVELOPE_ARRAY_KEYS = Set.of(
            "items", "data", "content", "results", "records", "elements", "values", "entries"
    );

    private ResponseCollections() {
    }

    /**
     * Returns the collection items, or empty when the response has no unambiguous item array.
     */
    static Optional<JsonArray> items(JsonElement root) {
        if (root == null || root.isJsonNull()) {
            return Optional.empty();
        }
        if (root.isJsonArray()) {
            return Optional.of(root.getAsJsonArray());
        }
        if (!root.isJsonObject()) {
            return Optional.empty();
        }
        return envelopeArray(root.getAsJsonObject()).or(() -> singleArray(root.getAsJsonObject()));
    }

    private static Optional<JsonArray> envelopeArray(JsonObject object) {
        return object.entrySet().stream()
                .filter(entry -> isArray(entry.getValue()))
                .filter(entry -> ENVELOPE_ARRAY_KEYS.contains(WordUtils.normalizeAlphaNumeric(entry.getKey())))
                .map(entry -> entry.getValue().getAsJsonArray())
                .findFirst();
    }

    private static Optional<JsonArray> singleArray(JsonObject object) {
        List<JsonArray> arrays = object.entrySet().stream()
                .map(Map.Entry::getValue)
                .filter(ResponseCollections::isArray)
                .map(JsonElement::getAsJsonArray)
                .toList();
        return arrays.size() == 1 ? Optional.of(arrays.getFirst()) : Optional.empty();
    }

    private static boolean isArray(JsonElement element) {
        return element != null && element.isJsonArray();
    }
}
