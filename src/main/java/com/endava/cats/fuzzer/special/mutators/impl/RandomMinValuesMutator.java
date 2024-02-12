package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Sends a random number min values in the target field.
 */
@Singleton
public class RandomMinValuesMutator implements Mutator {
    private static final List<Object> MIN_VALUES = List.of(Float.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE,
            Double.MIN_VALUE, Byte.MIN_VALUE, Short.MIN_VALUE, Float.MIN_NORMAL, Double.MIN_NORMAL, -Float.MIN_VALUE,
            -Float.MIN_NORMAL, -Double.MIN_VALUE, -Double.MIN_NORMAL);

    private final CatsUtil catsUtil;

    @Inject
    public RandomMinValuesMutator(CatsUtil catsUtil) {
        this.catsUtil = catsUtil;
    }

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object toReplaceWith = MIN_VALUES.get(CatsUtil.random().nextInt(MIN_VALUES.size()));
        return catsUtil.justReplaceField(inputJson, selectedField, toReplaceWith).json();
    }

    @Override
    public String name() {
        return "random min value";
    }
}