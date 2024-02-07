package com.endava.cats.fuzzer.special.mutators;

import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sends null value in the target field.
 */
@Singleton
public class NullStringMutator implements Mutator {
    private final CatsUtil catsUtil;

    @Inject
    public NullStringMutator(CatsUtil catsUtil) {
        this.catsUtil = catsUtil;
    }

    @Override
    public String mutate(String inputJson, String selectedField) {
        return catsUtil.justReplaceField(inputJson, selectedField, null).json();
    }

    @Override
    public String name() {
        return "null";
    }
}