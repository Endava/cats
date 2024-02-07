package com.endava.cats.fuzzer.special.mutators;

import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sends a random unicode value in the target field.
 */
@Singleton
public class RandomUnicodeStringMutator implements Mutator {
    private static final int BOUND = 100;
    @Inject
    CatsUtil catsUtil;

    @Override
    public String mutate(String inputJson, String selectedField) {
        int size = ThreadLocalRandom.current().nextInt(BOUND);
        return catsUtil.justReplaceField(inputJson, selectedField, RandomStringUtils.random(size)).json();
    }

    @Override
    public String name() {
        return "random unicode";
    }
}
