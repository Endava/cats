package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

/**
 * Sends null value in the target field.
 */
@Singleton
public class NullStringMutator implements Mutator {

    @Override
    public String mutate(String inputJson, String selectedField) {
        return CatsUtil.justReplaceField(inputJson, selectedField, null).json();
    }

    @Override
    public String description() {
        return "replace field with null";
    }
}
