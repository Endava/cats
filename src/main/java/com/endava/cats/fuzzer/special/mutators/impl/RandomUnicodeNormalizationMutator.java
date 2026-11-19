package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Replaces the target field with NFKC compatibility-expanding or Best-Fit syntax-transforming
 * Unicode strings straddling native allocator slab boundaries.
 */
@Singleton
public class RandomUnicodeNormalizationMutator implements BodyMutator {

    @Override
    public String mutate(String inputJson, String selectedField) {
        return mutateWithSchema(inputJson, selectedField, null);
    }

    @Override
    public String mutate(String inputJson, String selectedField, FuzzingData fuzzingData) {
        return mutateWithSchema(inputJson, selectedField, Mutator.resolveFieldSchema(fuzzingData, selectedField));
    }

    private static String mutateWithSchema(String inputJson, String selectedField, Schema<?> schema) {
        List<Object> candidates = UnicodeGenerator.generateNormalizationDifferentialPayloads(schema);
        Object selected = candidates.get(CatsRandom.instance().nextInt(candidates.size()));
        return CatsUtil.justReplaceField(inputJson, selectedField, selected).json();
    }

    @Override
    public String description() {
        return "replace field with NFKC compatibility-expanding or Best-Fit syntax-transforming Unicode payloads";
    }
}
