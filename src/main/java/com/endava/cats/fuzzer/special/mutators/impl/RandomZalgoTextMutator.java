package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sends a random zalgo text in the target field.
 */
@Singleton
public class RandomZalgoTextMutator implements Mutator {
    private static final String[] ZALGO_CHARACTERS = {
            "\u030d", "\u030e", "\u0304", "\u0305", "\u033f", "\u0311", "\u0306", "\u0310",
            "\u0352", "\u0357", "\u0351", "\u0307", "\u0308", "\u030a", "\u0342", "\u0343",
            "\u0344", "\u034a", "\u034b", "\u034c", "\u0303", "\u0302", "\u030c", "\u0350",
            "\u0300", "\u0301", "\u030b", "\u030f", "\u0312", "\u0313", "\u0314", "\u033d",
            "\u0309", "\u0363", "\u0364", "\u0365", "\u0366", "\u0367", "\u0368", "\u0369",
            "\u036a", "\u036b", "\u036c", "\u036d", "\u036e", "\u036f", "\u033e", "\u035b",
            "\u0346", "\u031a"
    };

    private final CatsUtil catsUtil;

    /**
     * Creates a new instance.
     *
     * @param catsUtil utility class
     */
    @Inject
    public RandomZalgoTextMutator(CatsUtil catsUtil) {
        this.catsUtil = catsUtil;
    }

    @Override
    public String mutate(String inputJson, String selectedField) {
        String existingValue = String.valueOf(JsonUtils.getVariableFromJson(inputJson, selectedField));
        String valueWithZalgo = generateZalgoText(existingValue);

        return catsUtil.justReplaceField(inputJson, selectedField, valueWithZalgo).json();
    }

    @Override
    public String description() {
        return "replace field with random zalgo text";
    }

    public static String generateZalgoText(String inputText) {
        StringBuilder zalgoText = new StringBuilder();

        for (int i = 0; i < inputText.length(); i++) {
            zalgoText.append(inputText.charAt(i));

            int numZalgoChars = 3 + CatsUtil.random().nextInt(7);
            for (int j = 0; j < numZalgoChars; j++) {
                zalgoText.append(ZALGO_CHARACTERS[CatsUtil.random().nextInt(ZALGO_CHARACTERS.length)]);
            }
        }

        return zalgoText.toString();
    }
}
