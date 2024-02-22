package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * Sends a random unicode value in the target field.
 */
@Singleton
public class RandomStringMutator implements Mutator {
    private static final int BOUND = 100;

    @Override
    public String mutate(String inputJson, String selectedField) {
        int size = CatsUtil.random().nextInt(BOUND);
        return CatsUtil.justReplaceField(inputJson, selectedField, RandomStringUtils.random(size)).json();
    }

    @Override
    public String description() {
        return "replace field with random unicode strings";
    }
}
