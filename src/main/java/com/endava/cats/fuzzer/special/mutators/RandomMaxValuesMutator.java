package com.endava.cats.fuzzer.special.mutators;

import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sends a random number max values in the target field.
 */
public class RandomMaxValuesMutator implements Mutator {
    private static final List<Object> MAX_VALUES = List.of(Float.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE,
            Double.MAX_VALUE, Byte.MAX_VALUE, Short.MAX_VALUE);
    @Inject
    CatsUtil catsUtil;

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object toReplaceWith = MAX_VALUES.get(ThreadLocalRandom.current().nextInt(MAX_VALUES.size()));
        return catsUtil.justReplaceField(inputJson, selectedField, toReplaceWith).json();
    }

    @Override
    public String name() {
        return "number max value";
    }
}