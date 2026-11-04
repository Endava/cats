package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

/**
 * Mutates the existing field value by substituting Latin characters with Unicode homoglyphs,
 * injecting zero-width characters, or wrapping with bidirectional override controls.
 */
@Singleton
public class RandomHomoglyphAndInvisibleCharsMutator implements BodyMutator {
    private static final String RLO = "\u202E";
    private static final String PDF = "\u202C";
    private static final List<String> INVISIBLE_CHARS = List.of(
            "\u200B", "\u200C", "\u200D", "\u2060", "\uFEFF");

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object existing = JsonUtils.getVariableFromJson(inputJson, selectedField);
        String baseValue = JsonUtils.isNotSet(String.valueOf(existing)) ? "catsValue" : String.valueOf(existing);
        String mutated = applyUnicodeMutation(baseValue);
        return CatsUtil.justReplaceField(inputJson, selectedField, mutated).json();
    }

    static String applyUnicodeMutation(String baseValue) {
        String source = baseValue.isEmpty() ? "catsValue" : baseValue;
        return switch (CatsRandom.instance().nextInt(3)) {
            case 0 -> replaceWithHomoglyph(source);
            case 1 -> injectInvisibleChar(source);
            default -> RLO + new StringBuilder(source).reverse() + PDF;
        };
    }

    private static String replaceWithHomoglyph(String source) {
        Map<Character, Character> homoglyphs = UnicodeGenerator.getHomoglyphs();
        for (int i = 0; i < source.length(); i++) {
            Character replacement = homoglyphs.get(source.charAt(i));
            if (replacement != null) {
                return source.substring(0, i) + replacement + source.substring(i + 1);
            }
        }
        return injectInvisibleChar(source);
    }

    private static String injectInvisibleChar(String source) {
        String invisible = INVISIBLE_CHARS.get(CatsRandom.instance().nextInt(INVISIBLE_CHARS.size()));
        return CatsUtil.insertInTheMiddle(source, invisible, true);
    }

    @Override
    public String description() {
        return "mutate existing field value with Unicode homoglyphs, zero-width characters, or bidirectional overrides";
    }
}
