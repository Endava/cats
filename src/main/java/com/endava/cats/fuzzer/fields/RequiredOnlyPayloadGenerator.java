package com.endava.cats.fuzzer.fields;

import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import com.google.common.base.Splitter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates request payloads containing only properties marked as required for the generated payload,
 * or strips unmarked server-generated metadata fields when the schema declares no required fields.
 */
public final class RequiredOnlyPayloadGenerator {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(RequiredOnlyPayloadGenerator.class);
    private static final Set<String> PRIMARY_ID_FIELDS = Set.of("id", "uuid", "guid");

    private RequiredOnlyPayloadGenerator() {
        // utility class
    }

    public static String generate(String payload, List<String> requiredFields) {
        return generate(payload, requiredFields, null, false);
    }

    /**
     * Retains required properties and recursively applies the same rule to objects and array items.
     * When {@code requiredFields} is empty on a body request, strips unmarked server-generated metadata fields
     * (primary IDs, resource-qualified IDs, audit timestamps, and hypermedia {@code _*} properties) at the root level
     * while retaining business fields.
     *
     * @param payload        the generated request payload
     * @param requiredFields required field paths for the exact generated schema variant
     * @param contractPath   the OpenAPI path template
     * @param bodyMethod     whether the HTTP method sends a request body
     * @return a JSON payload containing only required (or non-metadata) properties
     */
    public static String generate(String payload, List<String> requiredFields, String contractPath, boolean bodyMethod) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }

        try {
            JsonElement root = JsonUtils.parseAsJsonElement(payload);
            Set<String> required = requiredFields == null ? Collections.emptySet() : Set.copyOf(requiredFields);
            if (required.isEmpty() && bodyMethod) {
                JsonElement stripped = stripServerGeneratedFields(root, extractResourceIdNames(contractPath));
                if (stripped.isJsonObject() && !stripped.getAsJsonObject().isEmpty() && !stripped.equals(root)) {
                    return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(stripped);
                }
            }
            JsonElement requiredOnly = retainRequired(root, required, "");
            return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(requiredOnly);
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not create a required-only payload. Using the original payload: {}", exception.getMessage());
            return payload;
        }
    }

    private static JsonElement stripServerGeneratedFields(JsonElement value, Set<String> resourceIdNames) {
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(item -> result.add(stripServerGeneratedFieldsFromObject(item, resourceIdNames)));
            return result;
        }
        return stripServerGeneratedFieldsFromObject(value, resourceIdNames);
    }

    private static JsonElement stripServerGeneratedFieldsFromObject(JsonElement value, Set<String> resourceIdNames) {
        if (!value.isJsonObject()) {
            return value.deepCopy();
        }
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> property : value.getAsJsonObject().entrySet()) {
            if (!isServerGeneratedField(property.getKey(), resourceIdNames)) {
                result.add(property.getKey(), property.getValue().deepCopy());
            }
        }
        return result;
    }

    private static boolean isServerGeneratedField(String rawKey, Set<String> resourceIdNames) {
        if (rawKey == null || rawKey.isBlank()) {
            return false;
        }
        if (rawKey.startsWith("_")) {
            return true;
        }
        String normalized = normalize(rawKey);
        if (PRIMARY_ID_FIELDS.contains(normalized) || resourceIdNames.contains(normalized)) {
            return true;
        }
        return WordUtils.isAuditTimestampField(rawKey);
    }

    private static Set<String> extractResourceIdNames(String contractPath) {
        if (contractPath == null || contractPath.isBlank()) {
            return Set.of();
        }
        List<String> segments = Splitter.on('/').omitEmptyStrings().splitToList(contractPath);
        for (int i = segments.size() - 1; i >= 0; i--) {
            String seg = segments.get(i);
            if (!seg.startsWith("{")) {
                String norm = normalize(seg);
                if (!norm.isEmpty()) {
                    return WordUtils.nounForms(norm).stream()
                            .map(form -> form + "id").collect(Collectors.toUnmodifiableSet());
                }
            }
        }
        return Set.of();
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
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
