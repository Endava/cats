package com.endava.cats.report.error.fields;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Unwraps error bodies that nest the error in a single envelope object, such as
 * {@code {"error": {"code": "E9", "message": "Quota exceeded"}}} or {@code {"failure": {...}}}.
 */
public final class ErrorEnvelope {
    private static final Set<String> ENVELOPE_FIELDS = Set.of("error", "fault", "failure");

    private ErrorEnvelope() {
    }

    /**
     * Returns the error object with its envelope flattened: the fields of the envelope object join those of the
     * body, and win when both have the same field. A body without an envelope, or with several, is returned as is.
     *
     * @param body the error body
     * @return the body with the fields of its single envelope object at the top level
     */
    public static JsonObject unwrap(JsonObject body) {
        List<Map.Entry<String, JsonElement>> envelopes = body.entrySet().stream()
                .filter(entry -> isEnvelope(entry.getKey(), entry.getValue())).toList();
        return envelopes.size() == 1 ? flatten(body, envelopes.getFirst()) : body;
    }

    private static boolean isEnvelope(String field, JsonElement value) {
        return value.isJsonObject() && ENVELOPE_FIELDS.contains(field.toLowerCase(Locale.ROOT));
    }

    private static JsonObject flatten(JsonObject body, Map.Entry<String, JsonElement> envelope) {
        JsonObject flat = new JsonObject();
        body.entrySet().stream().filter(entry -> !entry.getKey().equals(envelope.getKey()))
                .forEach(entry -> flat.add(entry.getKey(), entry.getValue()));
        envelope.getValue().getAsJsonObject().entrySet().forEach(entry -> flat.add(entry.getKey(), entry.getValue()));
        return flat;
    }
}
