package com.endava.cats.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * This class is used to serialize objects with a maximum depth. It's useful when serializing objects that contain circular references that can exponentially grow
 * to exceed the maximum size that Jackson can serialize without streaming.
 */
public class DepthLimitingSerializer extends JsonSerializer<Object> {
    private final int maxDepth;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ThreadLocal<Integer> currentDepth = ThreadLocal.withInitial(() -> 0);

    public DepthLimitingSerializer(int depth) {
        maxDepth = depth;
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        try {
            currentDepth.set(0);
            serializeWithDepth(value, gen);
        } finally {
            currentDepth.remove();
        }
    }

    private void serializeWithDepth(Object value, JsonGenerator gen) throws IOException {
        int depth = currentDepth.get();

        if (depth >= maxDepth || value == null) {
            return;
        }

        currentDepth.set(depth + 1);

        try {
            if (value.getClass().isArray()) {
                serializeArray(value, gen);
            } else if (value instanceof Map) {
                serializeMap((Map<?, ?>) value, gen);
            } else if (value instanceof Collection) {
                serializeCollection((Collection<?>) value, gen);
            } else {
                serializePrimitive(value, gen);
            }
        } finally {
            currentDepth.set(depth);
        }
    }

    private void serializePrimitive(Object value, JsonGenerator gen) throws IOException {
        mapper.writeValue(gen, value);
    }

    private void serializeCollection(Collection<?> value, JsonGenerator gen) throws IOException {
        gen.writeStartArray();
        for (Object item : value) {
            if (item != null) {
                serializeWithDepth(item, gen);
            }
        }
        gen.writeEndArray();
    }

    private void serializeMap(Map<?, ?> value, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (isValidMapEntry(entry)) {
                gen.writeFieldName(entry.getKey().toString());
                serializeWithDepth(entry.getValue(), gen);
            }
        }
        gen.writeEndObject();
    }

    private boolean isValidMapEntry(Map.Entry<?, ?> entry) {
        return entry.getKey() != null && entry.getValue() != null && currentDepth.get() < maxDepth;
    }

    private void serializeArray(Object value, JsonGenerator gen) throws IOException {
        gen.writeStartArray();
        int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
            Object item = Array.get(value, i);
            if (item != null) {
                serializeWithDepth(item, gen);
            }
        }
        gen.writeEndArray();
    }
}
