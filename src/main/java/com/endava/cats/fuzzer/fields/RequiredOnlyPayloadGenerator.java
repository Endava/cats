package com.endava.cats.fuzzer.fields;

import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates request payloads containing only properties marked as required for the generated payload.
 */
final class RequiredOnlyPayloadGenerator {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(RequiredOnlyPayloadGenerator.class);

    private RequiredOnlyPayloadGenerator() {
        // utility class
    }

    /**
     * Retains required properties and recursively applies the same rule to objects and array items.
     *
     * @param payload        the generated request payload
     * @param requiredFields required field paths for the exact generated schema variant
     * @return a JSON payload containing only required properties
     */
    static String generate(String payload, List<String> requiredFields) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }

        try {
            JsonElement root = JsonUtils.parseAsJsonElement(payload);
            Set<String> required = requiredFields == null ? Collections.emptySet() : Set.copyOf(requiredFields);
            JsonElement requiredOnly = retainRequired(root, required, "");
            return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(requiredOnly);
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not create a required-only payload. Using the original payload: {}", exception.getMessage());
            return payload;
        }
    }

    private static JsonElement retainRequired(JsonElement value, Set<String> requiredFields, String parent) {
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(item -> result.add(retainRequired(item, requiredFields, parent)));
            return result;
        }

        if (!value.isJsonObject()) {
            return value.deepCopy();
        }

        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> property : value.getAsJsonObject().entrySet()) {
            String propertyPath = appendPath(parent, property.getKey());
            if (requiredFields.contains(propertyPath)) {
                result.add(property.getKey(), retainRequired(property.getValue(), requiredFields, propertyPath));
            }
        }
        return result;
    }

    private static String appendPath(String parent, String property) {
        return parent.isEmpty() ? property : parent + "#" + property;
    }
}
