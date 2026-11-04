package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Wraps the existing value of the target field in unexpected array/object containers
 * or replaces it with polymorphic/reference/prototype gadget objects.
 */
@Singleton
public class RandomContainerWrappingAndGadgetMutator implements BodyMutator {
    private static final List<BiFunction<String, Object, Object>> MUTATION_STRATEGIES = List.of(
            (_, current) -> List.of(current),
            (_, current) -> List.of(List.of(current)),
            (leafKey, current) -> Map.of(leafKey, current),
            (_, current) -> Map.of("value", current, "$ref", "#/"),
            (_, _) -> Map.of("@type", "java.lang.AutoCloseable", "@class", "java.lang.AutoCloseable"),
            (_, _) -> Map.of("__proto__", Map.of("admin", true), "constructor", Map.of("prototype", Map.of())));

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object rawCurrent = JsonUtils.getVariableFromJson(inputJson, selectedField);
        Object safeCurrent = rawCurrent == null || JsonUtils.isNotSet(String.valueOf(rawCurrent))
                ? "catsValue"
                : rawCurrent;
        String leafKey = extractLeafKey(selectedField);
        Object wrapped = selectStrategy().apply(leafKey, safeCurrent);
        return CatsUtil.justReplaceField(inputJson, selectedField, wrapped).json();
    }

    static String extractLeafKey(String selectedField) {
        if (selectedField == null || selectedField.isBlank()) {
            return "value";
        }
        int lastSep = selectedField.lastIndexOf('#');
        return lastSep >= 0 && lastSep < selectedField.length() - 1
                ? selectedField.substring(lastSep + 1)
                : selectedField;
    }

    private static BiFunction<String, Object, Object> selectStrategy() {
        return MUTATION_STRATEGIES.get(CatsRandom.instance().nextInt(MUTATION_STRATEGIES.size()));
    }

    @Override
    public String description() {
        return "wrap existing field value in nested containers or replace with polymorphic and prototype gadget objects";
    }
}
