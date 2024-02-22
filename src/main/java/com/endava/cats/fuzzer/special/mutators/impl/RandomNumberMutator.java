package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;


/**
 * Sends a random number in the target field.
 */
@Singleton
public class RandomNumberMutator implements Mutator {
    private static final int BOUND = 100;

    @Override
    public String mutate(String inputJson, String selectedField) {
        int size = CatsUtil.random().nextInt(BOUND);
        return CatsUtil.justReplaceField(inputJson, selectedField, RandomStringUtils.randomNumeric(size)).json();
    }

    @Override
    public String description() {
        return "replace field with random long numbers";
    }
}
