package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Identifies either the request root or a nested field for JSON payload mutations.
 */
record JsonPayloadTarget(String path, boolean root, Schema<?> schema) {
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
        return new JsonPayloadTarget("$", true, resolveSchema(schema, schemaMap));
    }

    static JsonPayloadTarget field(String path, Schema<?> schema, Map<String, Schema> schemaMap) {
        return new JsonPayloadTarget(path, false, resolveSchema(schema, schemaMap));
    }

    Optional<JsonElement> valueFrom(String payload) {
        if (root) {
            return Optional.of(JsonParser.parseString(payload));
        }

        String serialized = JsonUtils.serialize(JsonUtils.getVariableFromJson(payload, path));
        if (serialized == null) {
            return Optional.empty();
        }
        return Optional.of(JsonParser.parseString(serialized));
    }

    Optional<JsonArray> arrayFrom(String payload) {
        return valueFrom(payload).filter(JsonElement::isJsonArray).map(JsonElement::getAsJsonArray);
    }

    Optional<JsonObject> objectFrom(String payload) {
        return valueFrom(payload).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject);
    }

    String replaceValueIn(String payload, JsonElement value) {
        return root ? value.toString() : CatsUtil.justReplaceField(payload, path, value.toString()).json();
    }

    String displayName() {
        return root ? "root" : path;
    }

    static Schema<?> resolveSchema(Schema<?> schema, Map<String, Schema> schemaMap) {
        if (schema == null || schemaMap == null) {
            return schema;
        }

        Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Schema<?> resolved = schema;
        while (resolved.get$ref() != null && visited.add(resolved)) {
            String schemaName = CatsModelUtils.getSimpleRef(resolved.get$ref());
            Schema<?> referenced = schemaName == null ? null : schemaMap.get(schemaName);
            if (referenced == null) {
                break;
            }
            resolved = referenced;
        }
        return resolved;
    }
}
