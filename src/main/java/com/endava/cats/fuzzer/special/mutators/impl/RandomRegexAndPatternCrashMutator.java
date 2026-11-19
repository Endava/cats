package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * Replaces the target field with regex-compiler/serialization stress patterns,
 * multi-byte case-folded backreference strings, or multiline anchor bypass sequences.
 */
@Singleton
public class RandomRegexAndPatternCrashMutator implements BodyMutator {

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object existing = JsonUtils.getVariableFromJson(inputJson, selectedField);
        String base = existing == null || JsonUtils.isNotSet(String.valueOf(existing)) ? "skifSKIF" : String.valueOf(existing);
        List<String> candidates = buildCandidates(base);
        String selected = candidates.get(CatsRandom.instance().nextInt(candidates.size()));
        return CatsUtil.justReplaceField(inputJson, selectedField, selected).json();
    }

    static List<String> buildCandidates(String base) {
        String tag = CatsRandom.alphanumeric(3).toLowerCase(Locale.ROOT);
        int repeatBound = 63 + CatsRandom.instance().nextInt(3);
        return List.of(
                "(?i)(?<k%s>[a-z\\u0130\\u00DF]+)\\k<k%s>{1,%d}".formatted(tag, tag, repeatBound),
                "(?i-m:(?:(?>[a-z0-9_]+)|(?<g%s>\\w+))\\k<g%s>)".formatted(tag, tag),
                "[[a-z0-9]&&[^%s]]{0,255}(?:(\\w+)\\1)?".formatted(tag),
                base + "s\u017Fi\u0130k\u212A\uFB00",
                base + "\n" + tag + "\u0130\u00DF");
    }

    @Override
    public String description() {
        return "replace field with regex compiler/Regexp#to_s stress patterns, case-folded backreferences, or multiline anchor payloads";
    }
}
