package com.endava.cats.fuzzer.special.mutators;

import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.concurrent.ThreadLocalRandom;


/**
 * Sends a random number in the target field.
 */
public class RandomNumberMutator implements Mutator {
    private static final int BOUND = 100;

    @Inject
    CatsUtil catsUtil;

    @Override
    public String mutate(String inputJson, String selectedField) {
        int size = ThreadLocalRandom.current().nextInt(BOUND);
        return catsUtil.justReplaceField(inputJson, selectedField, RandomStringUtils.randomAlphanumeric(size)).json();
    }

    @Override
    public String name() {
        return "random number";
    }
}
