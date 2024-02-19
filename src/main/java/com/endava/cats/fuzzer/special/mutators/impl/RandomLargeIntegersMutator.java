package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;

import java.math.BigInteger;

/**
 * Sends a random large integers in the target field.
 */
@Singleton
public class RandomLargeIntegersMutator implements Mutator {
    private static final int ITERATIONS = 5;
    private static final int LENGTH = 20;

    private final CatsUtil catsUtil;

    @Inject
    public RandomLargeIntegersMutator(CatsUtil catsUtil) {
        this.catsUtil = catsUtil;
    }

    @Override
    public String mutate(String inputJson, String selectedField) {
        int i = 0;
        StringBuilder largeNumberBuilder = new StringBuilder();
        while (i < ITERATIONS) {
            largeNumberBuilder.append(RandomStringUtils.randomNumeric(LENGTH));
            i++;
        }

        return catsUtil.justReplaceField(inputJson, selectedField, new BigInteger(largeNumberBuilder.toString())).json();
    }

    @Override
    public String description() {
        return "replace field with random large integers";
    }
}