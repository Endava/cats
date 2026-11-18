package com.endava.cats.util;

import com.endava.cats.args.ReportingArguments;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Custom Gson serializer for key-value pairs. Masks specific keys during serialization.
 */
public class KeyValueSerializer implements JsonSerializer<KeyValuePair<String, Object>> {

    private final Set<String> toMask;
    private final HeaderPresentation headers;
    private final Predicate<KeyValuePair<String, Object>> publicRequestHeader;

    /**
     * Constructs a new instance of KeyValueSerializer with the specified keys to mask.
     *
     * @param keysToMask The set of keys to be masked during serialization.
     */
    public KeyValueSerializer(Set<String> keysToMask) {
        this.toMask = keysToMask;
        this.headers = null;
        this.publicRequestHeader = _ -> false;
    }

    public KeyValueSerializer(ReportingArguments reportingArguments) {
        this(reportingArguments, _ -> false);
    }

    public KeyValueSerializer(ReportingArguments reportingArguments, Predicate<KeyValuePair<String, Object>> publicRequestHeader) {
        this.toMask = Set.of();
        this.headers = new HeaderPresentation(reportingArguments);
        this.publicRequestHeader = publicRequestHeader;
    }

    @Override
    public JsonElement serialize(KeyValuePair<String, Object> src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("key", src.getKey());
        jsonObject.addProperty("value", String.valueOf(mask(src)));
        return jsonObject;
    }

    private Object mask(KeyValuePair<String, Object> header) {
        if (SensitiveDataPolicy.REDACTED.equals(header.getValue())) {
            return SensitiveDataPolicy.REDACTED;
        }
        if (toMask.stream().anyMatch(key -> key.equalsIgnoreCase(header.getKey()))) {
            return SensitiveDataPolicy.placeholder(SensitiveDataPolicy.headerEnvironmentVariable(header.getKey()));
        }
        return headers == null ? header.getValue() : headers.value(header, publicRequestHeader.test(header));
    }
}
