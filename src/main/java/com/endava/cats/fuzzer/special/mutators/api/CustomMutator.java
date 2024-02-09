package com.endava.cats.fuzzer.special.mutators.api;

import com.endava.cats.json.JsonUtils;
import com.endava.cats.strategy.CommonWithinMethods;
import com.endava.cats.util.CatsUtil;

/**
 * Executes mutator logic from a custom mutator file.
 * <p>
 * TODO:
 * - handle dynamic values in customMutator
 * - list type of mutators registered
 * - list type of custom mutators supported
 */
public class CustomMutator implements Mutator {
    private final CustomMutatorConfig customMutatorConfig;
    private final CatsUtil catsUtil;

    public CustomMutator(CustomMutatorConfig customMutatorConfig, CatsUtil catsUtil) {
        this.customMutatorConfig = customMutatorConfig;
        this.catsUtil = catsUtil;
    }

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object existingValue = JsonUtils.getVariableFromJson(inputJson, selectedField);

        Object valueToFuzz = CatsUtil.selectRandom(customMutatorConfig.values());

        return switch (customMutatorConfig.type()) {
            case TRAIL -> {
                String valueToReplace = String.valueOf(existingValue) + valueToFuzz;
                yield catsUtil.justReplaceField(inputJson, selectedField, valueToReplace).json();
            }
            case INSERT -> {
                String valueToReplace = CommonWithinMethods.insertInTheMiddle(String.valueOf(existingValue), String.valueOf(valueToFuzz), true);
                yield catsUtil.justReplaceField(inputJson, selectedField, valueToReplace).json();
            }
            case PREFIX -> {
                String valueToReplace = valueToFuzz + String.valueOf(existingValue);
                yield catsUtil.justReplaceField(inputJson, selectedField, valueToReplace).json();
            }
            case REPLACE -> catsUtil.justReplaceField(inputJson, selectedField, valueToFuzz).json();
            case REPLACE_BODY -> String.valueOf(valueToFuzz);
            case IN_BODY -> {
                char firstChar = inputJson.charAt(0);
                yield firstChar + String.valueOf(valueToFuzz) + "," + inputJson.substring(1);
            }
        };
    }

    @Override
    public String name() {
        return customMutatorConfig.name();
    }
}
