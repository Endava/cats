package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

/**
 * Mutates the JSON payload with a substring of random length from the input JSON.
 */
@Singleton
public class RandomPayloadSizeMutator implements BodyMutator {

    @Override
    public String mutate(String inputJson, String selectedField) {
        int size = CatsUtil.random().nextInt(inputJson.length());
        return CatsUtil.justReplaceField(inputJson, selectedField, inputJson.substring(0, size)).json();
    }

    @Override
    public String description() {
        return "replace the payload with a substring of random length";
    }
}
