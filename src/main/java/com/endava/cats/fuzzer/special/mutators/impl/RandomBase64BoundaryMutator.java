package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.generator.simple.Base64BoundaryGenerator;
import com.endava.cats.util.CatsUtil;
import jakarta.inject.Singleton;

/**
 * Replaces the target field with a randomized SIMD-stride Base64 string carrying
 * underflow padding, non-canonical pad bits, or URL-safe/MIME boundary tails.
 */
@Singleton
public class RandomBase64BoundaryMutator implements BodyMutator {

    @Override
    public String mutate(String inputJson, String selectedField) {
        String mutated = Base64BoundaryGenerator.randomBoundaryValue();
        return CatsUtil.justReplaceField(inputJson, selectedField, mutated).json();
    }

    @Override
    public String description() {
        return "replace field with randomized SIMD-stride Base64 strings and malformed padding tails";
    }
}
