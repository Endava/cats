package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Sends a random number max values in the target field.
 */
@Singleton
public class RandomMaxValuesMutator implements BodyMutator {
    private static final List<Object> MAX_VALUES = List.of(Float.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE,
            Double.MAX_VALUE, Byte.MAX_VALUE, Short.MAX_VALUE);

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object toReplaceWith = MAX_VALUES.get(CatsUtil.random().nextInt(MAX_VALUES.size()));
        return CatsUtil.justReplaceField(inputJson, selectedField, toReplaceWith).json();
    }

    @Override
    public String description() {
        return "replace field with random max values for int, long, float or double";
    }
}