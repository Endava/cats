package com.endava.cats.fuzzer.special.mutators;

import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

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
        List<String> controlChars = UnicodeGenerator.getControlCharsFields();
        String randomControlChars = CatsUtil.random().ints(BOUND, 0, controlChars.size())
                .mapToObj(controlChars::get)
                .collect(Collectors.joining());

        return catsUtil.justReplaceField(inputJson, selectedField, randomControlChars).json();
    }

    @Override
    public String name() {
        return "random control chars";
    }
}