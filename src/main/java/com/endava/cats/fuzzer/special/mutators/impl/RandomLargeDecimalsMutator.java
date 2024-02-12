package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;

import java.math.BigDecimal;

/**
 * Sends a random large decimals in the target field.
 */
@Singleton
public class RandomLargeDecimalsMutator implements Mutator {
    private static final int ITERATIONS = 5;
    private static final int SCALE_ITERATIONS = 2;

    private static final int LENGTH = 20;

    private final CatsUtil catsUtil;

    @Inject
    public RandomLargeDecimalsMutator(CatsUtil catsUtil) {
        this.catsUtil = catsUtil;
    }

    @Override
    public String mutate(String inputJson, String selectedField) {
        int totalIterations = ITERATIONS + SCALE_ITERATIONS;
        StringBuilder largeNumberBuilder = new StringBuilder();

        for (int i = 0; i < totalIterations; i++) {
            if (i == ITERATIONS) {
                largeNumberBuilder.append(".");
            }
            largeNumberBuilder.append(RandomStringUtils.randomNumeric(LENGTH));
        }

        return catsUtil.justReplaceField(inputJson, selectedField, new BigDecimal(largeNumberBuilder.toString())).json();
    }

    @Override
    public String name() {
        return "random large decimals";
    }
}