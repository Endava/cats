package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

@Singleton
public class UppercaseExpandingLengthMutator implements BodyMutator {
    @Override
    public String mutate(String inputJson, String selectedField) {
        int length = String.valueOf(JsonUtils.getVariableFromJson(inputJson, selectedField)).length();
        String generated = CatsUtil.selectRandom(UnicodeGenerator.getUppercaseExpandingLength(), length);
        return CatsUtil.justReplaceField(inputJson, selectedField, generated).json();
    }

    @Override
    public String description() {
        return "replace field with strings that expand length when uppercased";
    }
}
