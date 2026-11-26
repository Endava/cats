package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Performs AFL-style cross-field splice/crossover mutations across two fields in the JSON payload:
 * swapping sibling values, inverting numeric/temporal ordering, or echoing a case-folded twin.
 */
@Singleton
public class RandomCrossFieldSwapAndSkewMutator implements BodyMutator {

    @Override
    public String mutate(String inputJson, String selectedField) {
        return mutate(inputJson, selectedField, null);
    }

    @Override
    public String mutate(String inputJson, String selectedField, FuzzingData fuzzingData) {
        if (JsonUtils.isEmptyPayload(inputJson) || selectedField == null || selectedField.isBlank()) {
            return inputJson;
        }
        List<String> siblings = resolveSiblingFields(inputJson, selectedField, fuzzingData);
        if (siblings.isEmpty()) {
            Object current = safeFieldValue(inputJson, selectedField);
            return CatsUtil.justReplaceField(inputJson, selectedField,
                    Map.of("primary", current, "confirm", current + "\u0130\u00DF")).json();
        }
        String sibling = siblings.get(CatsRandom.instance().nextInt(siblings.size()));
        return applyCrossFieldStrategy(inputJson, selectedField, sibling);
    }

    static List<String> resolveSiblingFields(String inputJson, String selectedField, FuzzingData fuzzingData) {
        if (fuzzingData != null && fuzzingData.getAllFieldsByHttpMethod() != null) {
            List<String> fromData = fuzzingData.getAllFieldsByHttpMethod().stream()
                    .filter(Predicate.not(selectedField::equals))
                    .filter(field -> !isAncestorOrDescendant(selectedField, field))
                    .filter(field -> JsonUtils.isFieldInJson(inputJson, field))
                    .sorted()
                    .toList();
            if (!fromData.isEmpty()) {
                return fromData;
            }
        }
        return extractTopLevelSiblings(inputJson, selectedField);
    }

    private static boolean isAncestorOrDescendant(String first, String second) {
        return first.startsWith(second + "#") || second.startsWith(first + "#")
                || first.startsWith(second + "[") || second.startsWith(first + "[");
    }

    private static List<String> extractTopLevelSiblings(String inputJson, String selectedField) {
        try {
            JsonElement root = JsonUtils.parseAsJsonElement(inputJson);
            if (root == null || !root.isJsonObject()) {
                return List.of();
            }
            return root.getAsJsonObject().entrySet().stream()
                    .map(Map.Entry::getKey)
                    .filter(Predicate.not(selectedField::equals))
                    .filter(field -> !isAncestorOrDescendant(selectedField, field))
                    .toList();
        } catch (RuntimeException _) {
            return List.of();
        }
    }

    private static String applyCrossFieldStrategy(String inputJson, String selectedField, String siblingField) {
        Object firstVal = safeFieldValue(inputJson, selectedField);
        Object secondVal = safeFieldValue(inputJson, siblingField);
        return switch (CatsRandom.instance().nextInt(3)) {
            case 0 -> replacePair(inputJson, selectedField, secondVal, siblingField, firstVal);
            case 1 -> replacePair(inputJson, selectedField, 999999999L, siblingField, -999999999L);
            default -> replacePair(inputJson, selectedField, firstVal + "\u0130\u00DF", siblingField, firstVal + "\u0131ss");
        };
    }

    private static String replacePair(String inputJson, String fieldA, Object valA, String fieldB, Object valB) {
        String stepOne = CatsUtil.justReplaceField(inputJson, fieldA, valA).json();
        if (!JsonUtils.isFieldInJson(stepOne, fieldB)) {
            return stepOne;
        }
        return CatsUtil.justReplaceField(stepOne, fieldB, valB).json();
    }

    private static Object safeFieldValue(String inputJson, String field) {
        Object value = JsonUtils.getVariableFromJson(inputJson, field);
        return value == null || JsonUtils.isNotSet(String.valueOf(value)) ? "catsValue" : value;
    }

    @Override
    public boolean isComposable() {
        return true;
    }

    @Override
    public String description() {
        return "swap values, invert numeric/temporal bounds, or echo case-folded values across two fields";
    }
}
