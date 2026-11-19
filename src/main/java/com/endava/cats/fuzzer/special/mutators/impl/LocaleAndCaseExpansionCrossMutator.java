package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.CrossLayerMutator;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;

/**
 * Pairs a case-sensitive {@code Accept-Language} locale header ({@code tr-TR}, {@code az-AZ}, {@code el-GR}, {@code lt-LT})
 * with a locale-sensitive dotted/dotless-I and case-expanding Unicode payload in the target body field.
 */
@Singleton
public class LocaleAndCaseExpansionCrossMutator implements CrossLayerMutator {
    private static final List<String> LOCALES = List.of(
            "tr-TR, az-AZ;q=0.9",
            "el-GR, lt-LT;q=0.9",
            "de-DE-u-co-phonebk, tr;q=0.8");

    @Override
    public String mutate(String inputJson, String selectedField) {
        return mutateWithSchema(inputJson, selectedField, null);
    }

    @Override
    public String mutate(String inputJson, String selectedField, FuzzingData fuzzingData) {
        return mutateWithSchema(inputJson, selectedField, Mutator.resolveFieldSchema(fuzzingData, selectedField));
    }

    private static String mutateWithSchema(String inputJson, String selectedField, Schema<?> schema) {
        List<Object> expanding = UnicodeGenerator.generateCaseExpandingPayloads(
                schema, UnicodeGenerator.getUppercaseExpandingLength());
        Object selected = expanding.get(CatsRandom.instance().nextInt(expanding.size()));
        return CatsUtil.justReplaceField(inputJson, selectedField, "iI\u0130\u0131\u017F\u212A" + selected).json();
    }

    @Override
    public Collection<CatsHeader> mutate(Collection<CatsHeader> headers) {
        String locale = LOCALES.get(CatsRandom.instance().nextInt(LOCALES.size()));
        return CrossLayerMutator.replaceHeader(headers, "Accept-Language", locale);
    }

    @Override
    public String description() {
        return "pair case-sensitive Accept-Language locales with Unicode case-expanding field values";
    }
}
