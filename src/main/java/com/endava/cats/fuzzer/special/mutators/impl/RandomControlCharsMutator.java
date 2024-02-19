package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sends random control chars in the target field.
 */
@Singleton
public class RandomControlCharsMutator implements Mutator {
    private static final int BOUND = 10;
    private final CatsUtil catsUtil;

    @Inject
    public RandomControlCharsMutator(CatsUtil catsUtil) {
        this.catsUtil = catsUtil;
    }

    @Override
    public String mutate(String inputJson, String selectedField) {
        String randomControlChars = UnicodeGenerator.generateRandomUnicodeString(BOUND, Character::isISOControl);

        return catsUtil.justReplaceField(inputJson, selectedField, randomControlChars).json();
    }

    @Override
    public String description() {
        return "replace field with random control chars";
    }
}