package com.endava.cats.fuzzer.special.mutators.api;

import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;

/**
 * Executes mutator logic from a custom mutator file.
 *
 * <p>
 * Custom mutators are parsed as {@code CustomMutatorConfig} and
 * then transformed into {@code CustomMutator} instances.
 * </p>
 */
public class CustomMutator implements BodyMutator {
    private final CustomMutatorConfig customMutatorConfig;

    public CustomMutator(CustomMutatorConfig customMutatorConfig) {
        this.customMutatorConfig = customMutatorConfig;
    }

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object existingValue = JsonUtils.getVariableFromJson(inputJson, selectedField);

        Object valueToFuzz = CatsUtil.selectRandom(customMutatorConfig.values());

        return switch (customMutatorConfig.type()) {
            case TRAIL -> {
                String valueToReplace = String.valueOf(existingValue) + valueToFuzz;
                yield CatsUtil.justReplaceField(inputJson, selectedField, valueToReplace).json();
            }
            case INSERT -> {
                String valueToReplace = CatsUtil.insertInTheMiddle(String.valueOf(existingValue), String.valueOf(valueToFuzz), true);
                yield CatsUtil.justReplaceField(inputJson, selectedField, valueToReplace).json();
            }
            case PREFIX -> {
                String valueToReplace = valueToFuzz + String.valueOf(existingValue);
                yield CatsUtil.justReplaceField(inputJson, selectedField, valueToReplace).json();
            }
            case REPLACE -> CatsUtil.justReplaceField(inputJson, selectedField, valueToFuzz).json();
            case REPLACE_BODY -> String.valueOf(valueToFuzz);
            case IN_BODY -> {
                char firstChar = inputJson.charAt(0);
                yield firstChar + String.valueOf(valueToFuzz) + "," + inputJson.substring(1);
            }
        };
    }

    @Override
    public String description() {
        return customMutatorConfig.name();
    }
}
