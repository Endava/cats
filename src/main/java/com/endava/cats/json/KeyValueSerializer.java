package com.endava.cats.json;

import com.endava.cats.model.KeyValuePair;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * Masks headers marked for masking.
 */
public class KeyValueSerializer implements JsonSerializer<KeyValuePair<String, Object>> {

    private final Set<String> toMask;

    public KeyValueSerializer(Set<String> keysToMask) {
        this.toMask = keysToMask;
    }

    @Override
    public JsonElement serialize(KeyValuePair<String, Object> src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("key", src.getKey());
        jsonObject.addProperty("value", String.valueOf(mask(src)));
        return jsonObject;
    }

    private Object mask(KeyValuePair<String, Object> header) {
        if (toMask.contains(header.getKey())) {
            return "$$" + header.getKey().replaceAll("[_-]*", "");
        }

        return header.getValue();
    }
}
