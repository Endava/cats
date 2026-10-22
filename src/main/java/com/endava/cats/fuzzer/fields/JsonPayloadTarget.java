package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import com.google.common.base.Splitter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Identifies either the request root or a nested field for JSON payload mutations.
 */
record JsonPayloadTarget(String path, boolean root, Schema<?> schema) {
    private static final Splitter PATH_SPLITTER = Splitter.on('#').omitEmptyStrings();

    static List<JsonPayloadTarget> candidatesFor(FuzzingData data) {
        Map<String, Schema> schemaMap = data.getSchemaMap();
        List<JsonPayloadTarget> targets = new ArrayList<>();
        targets.add(root(data.getReqSchema(), schemaMap));

        Map<String, Schema> propertyTypes = Optional.ofNullable(data.getRequestPropertyTypes()).orElseGet(Map::of);
        for (String field : Optional.ofNullable(data.getAllFieldsByHttpMethod()).orElseGet(Collections::emptySet)) {
            targets.add(field(field, propertyTypes.get(field), schemaMap));
        }
        return targets;
    }

    static JsonPayloadTarget root(Schema<?> schema, Map<String, Schema> schemaMap) {
        return new JsonPayloadTarget("$", true, CatsModelUtils.resolveSchema(schema, schemaMap));
    }

    static JsonPayloadTarget field(String path, Schema<?> schema, Map<String, Schema> schemaMap) {
        return new JsonPayloadTarget(path, false, CatsModelUtils.resolveSchema(schema, schemaMap));
    }

    Optional<JsonElement> valueFrom(String payload) {
        if (!JsonUtils.isValidJson(payload)) {
            return Optional.empty();
        }
        return valueFrom(JsonParser.parseString(payload));
    }

    Optional<JsonElement> valueFrom(JsonElement parsedRoot) {
        if (parsedRoot == null || parsedRoot.isJsonNull()) {
            return Optional.empty();
        }
        if (root) {
            return Optional.of(parsedRoot);
        }
        JsonElement current = parsedRoot;
        for (String segment : PATH_SPLITTER.split(path)) {
            current = stepThroughSegment(current, segment);
            if (current == null || current.isJsonNull()) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    private static JsonElement stepThroughSegment(JsonElement current, String segment) {
        JsonElement node = current;
        if (node.isJsonArray()) {
            JsonArray arr = node.getAsJsonArray();
            if (arr.isEmpty()) {
                return null;
            }
            node = arr.get(0);
        }
        if (node == null || !node.isJsonObject()) {
            return null;
        }
        String cleanSegment = segment.endsWith("[*]") ? segment.substring(0, segment.length() - 3) : segment;
        return node.getAsJsonObject().get(cleanSegment);
    }

    Optional<JsonArray> arrayFrom(String payload) {
        return valueFrom(payload).filter(JsonElement::isJsonArray).map(JsonElement::getAsJsonArray);
    }

    Optional<JsonArray> arrayFrom(JsonElement parsedRoot) {
        return valueFrom(parsedRoot).filter(JsonElement::isJsonArray).map(JsonElement::getAsJsonArray);
    }

    Optional<JsonObject> objectFrom(String payload) {
        return valueFrom(payload).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject);
    }

    Optional<JsonObject> objectFrom(JsonElement parsedRoot) {
        return valueFrom(parsedRoot).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject);
    }

    String replaceValueIn(String payload, JsonElement value) {
        return root ? value.toString() : CatsUtil.justReplaceField(payload, path, value.toString()).json();
    }

    String displayName() {
        return root ? "root" : path;
    }

    static Schema<?> resolveSchema(Schema<?> schema, Map<String, Schema> schemaMap) {
        return CatsModelUtils.resolveSchema(schema, schemaMap);
    }
}
