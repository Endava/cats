package com.endava.cats.fuzzer.fields;

import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.strategy.FuzzingStrategy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds reusable hostile JSON keys and values for unconstrained schemas.
 */
final class FreeFormPayloadGenerator {
    private static final int DEEP_OBJECT_DEPTH = 32;
    private static final String SIMPLE_VALUE = "catsFuzzyValue";

    private FreeFormPayloadGenerator() {
        // utility class
    }

    static PayloadSet generate(int largeStringsSize) {
        int largeStringRepetitions = Math.max(1, largeStringsSize / 4);
        String largeString = StringGenerator.generateLargeString(largeStringRepetitions);
        String largeUnicode = String.valueOf(FuzzingStrategy.getLargeValuesStrategy(largeStringsSize)
                .getFirst().getData());
        String zeroWidthKey = "cats" + String.join("", UnicodeGenerator.getZwCharsSmallListFields()) + "key";
        String controlChars = String.join("", UnicodeGenerator.getControlCharsFields());
        Map<String, JsonElement> reservedProperties = reservedProperties();

        List<HostileValue> hostileValues = new ArrayList<>();
        hostileValues.add(new HostileValue("an extremely long string value", new JsonPrimitive(largeString)));
        hostileValues.add(new HostileValue("an extremely large Unicode value", new JsonPrimitive(largeUnicode)));
        hostileValues.add(new HostileValue("control characters as a value", new JsonPrimitive(controlChars)));
        hostileValues.add(new HostileValue("an extreme numeric value",
                new JsonPrimitive(NumberGenerator.MOST_POSITIVE_INTEGER)));
        hostileValues.add(new HostileValue("a null value", JsonNull.INSTANCE));
        hostileValues.add(new HostileValue("a reserved-key object", objectFrom(reservedProperties)));
        hostileValues.add(new HostileValue("a deeply nested object", deeplyNestedObject()));
        hostileValues.add(new HostileValue("a heterogeneous array", heterogeneousArray()));

        return new PayloadSet(largeString, zeroWidthKey, controlChars, reservedProperties, hostileValues);
    }

    private static Map<String, JsonElement> reservedProperties() {
        Map<String, JsonElement> properties = new LinkedHashMap<>();
        properties.put("__proto__", new JsonPrimitive(SIMPLE_VALUE));
        properties.put("constructor", new JsonPrimitive(SIMPLE_VALUE));
        properties.put("prototype", new JsonPrimitive(SIMPLE_VALUE));
        properties.put("$where", new JsonPrimitive("sleep(5000)"));
        return properties;
    }

    private static JsonObject objectFrom(Map<String, JsonElement> properties) {
        JsonObject object = new JsonObject();
        properties.forEach((key, value) -> object.add(key, value.deepCopy()));
        return object;
    }

    private static JsonObject deeplyNestedObject() {
        JsonObject result = new JsonObject();
        JsonObject current = result;
        for (int i = 0; i < DEEP_OBJECT_DEPTH; i++) {
            JsonObject child = new JsonObject();
            current.add("catsNested" + i, child);
            current = child;
        }
        current.addProperty("value", UnicodeGenerator.getZalgoText());
        return result;
    }

    private static JsonArray heterogeneousArray() {
        JsonArray array = new JsonArray();
        array.add(JsonNull.INSTANCE);
        array.add(NumberGenerator.MOST_NEGATIVE_INTEGER);
        array.add(UnicodeGenerator.getBadPayload());
        array.add(deeplyNestedObject());
        return array;
    }

    record PayloadSet(String largeString, String zeroWidthKey, String controlChars,
                      Map<String, JsonElement> reservedProperties, List<HostileValue> hostileValues) {
    }

    record HostileValue(String description, JsonElement value) {
    }
}
