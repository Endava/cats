package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

/**
 * Inserts random values in the target field key.
 */
@Singleton
public class RandomControlCharsInFieldKeysMutator implements Mutator {
    private static final int BOUND = 2;

    @Override
    public String mutate(String inputJson, String selectedField) {
        String randomControlChars = UnicodeGenerator.generateRandomUnicodeString(BOUND, Character::isISOControl);

        return JsonUtils.insertCharactersInFieldKey(inputJson, selectedField, randomControlChars);
    }

    @Override
    public String description() {
        return "insert random control chars in field keys";
    }
}