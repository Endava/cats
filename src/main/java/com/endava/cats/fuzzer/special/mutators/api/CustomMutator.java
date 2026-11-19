package com.endava.cats.fuzzer.special.mutators.api;

import com.endava.cats.generator.Cloner;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;

import java.util.Collection;
import java.util.Set;

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
            case DUPLICATE_KEY -> duplicateKeyInRoot(inputJson, selectedField, valueToFuzz);
            case REPLACE_HEADER, TRAIL_HEADER -> inputJson;
        };
    }

    @Override
    public Collection<CatsHeader> mutate(Collection<CatsHeader> headers) {
        if (customMutatorConfig.type() != CustomMutatorConfig.Type.REPLACE_HEADER
                && customMutatorConfig.type() != CustomMutatorConfig.Type.TRAIL_HEADER) {
            return headers;
        }
        Object valueToFuzz = CatsUtil.selectRandom(customMutatorConfig.values());
        Set<CatsHeader> clone = Cloner.cloneMe(headers);
        if (clone.isEmpty()) {
            clone.add(CatsHeader.from("X-Cats-Custom", String.valueOf(valueToFuzz), false, null));
            return clone;
        }
        CatsHeader target = CatsUtil.selectRandom(clone);
        String newValue = customMutatorConfig.type() == CustomMutatorConfig.Type.TRAIL_HEADER
                ? target.getValue() + valueToFuzz
                : String.valueOf(valueToFuzz);
        clone.remove(target);
        clone.add(CatsHeader.from(target.getName(), newValue, target.isRequired(), target.getFormat()));
        return clone;
    }

    private static String duplicateKeyInRoot(String inputJson, String selectedField, Object valueToFuzz) {
        if (JsonUtils.isEmptyPayload(inputJson) || !inputJson.stripLeading().startsWith("{")) {
            return inputJson;
        }
        int lastSep = selectedField == null ? -1 : selectedField.lastIndexOf('#');
        String leafKey = lastSep >= 0 ? selectedField.substring(lastSep + 1) : String.valueOf(selectedField);
        String member = "\"%s\":%s,".formatted(JsonUtils.escape(leafKey), JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(valueToFuzz));
        int openBrace = inputJson.indexOf('{');
        return inputJson.substring(0, openBrace + 1) + member + inputJson.substring(openBrace + 1);
    }

    @Override
    public String description() {
        return customMutatorConfig.name();
    }
}
