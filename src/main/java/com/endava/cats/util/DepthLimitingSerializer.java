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
    private static int maxDepth;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ThreadLocal<Integer> currentDepth = ThreadLocal.withInitial(() -> 0);

    public DepthLimitingSerializer(int depth) {
        maxDepth = depth * 6;
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
                gen.writeStartArray();
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    Object item = Array.get(value, i);
                    if (item != null) {
                        serializeWithDepth(item, gen);
                    }
                }
                gen.writeEndArray();
            } else if (value instanceof Map) {
                gen.writeStartObject();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        if (currentDepth.get() < maxDepth) {
                            gen.writeFieldName(entry.getKey().toString());
                            serializeWithDepth(entry.getValue(), gen);
                        }
                    }
                }
                gen.writeEndObject();
            } else if (value instanceof Collection) {
                gen.writeStartArray();
                for (Object item : (Collection<?>) value) {
                    if (item != null) {
                        serializeWithDepth(item, gen);
                    }
                }
                gen.writeEndArray();
            } else {
                mapper.writeValue(gen, value);
            }
        } finally {
            currentDepth.set(depth);
        }
    }
}
