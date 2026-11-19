package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Stream;

/**
 * Splices exact unquoted JSON number lexemes (1,001-digit stream-constraint numbers, extreme exponents,
 * subnormal IEEE-754 boundaries, negative zero) or raw surrogate-escape literals into the target field.
 */
@Singleton
public class RandomRawNumericAndLexemeMutator implements BodyMutator {
    private static final List<String> RAW_LEXEMES = List.of(
            "-0.0",
            "2.2250738585072012e-308",
            "2.2250738585072011e-308",
            "4.9406564584124654e-324",
            "1.7976931348623157e308",
            "9007199254740993",
            "1e10000",
            "1e-10000",
            "1e-2147483648",
            "1" + "0".repeat(1000),
            "0." + "0".repeat(1000) + "1",
            "\"cats\\uD800\\u0000\\uFDFA\"");

    @Override
    public String mutate(String inputJson, String selectedField) {
        if (JsonUtils.isEmptyPayload(inputJson) || selectedField == null || selectedField.isBlank()) {
            return inputJson;
        }
        String lexeme = RAW_LEXEMES.get(CatsRandom.instance().nextInt(RAW_LEXEMES.size()));
        return spliceRawLexeme(inputJson, selectedField, lexeme);
    }

    static String spliceRawLexeme(String inputJson, String selectedField, String rawLexeme) {
        if (!JsonUtils.isFieldInJson(inputJson, selectedField)) {
            return inputJson;
        }
        try {
            String canonical = JsonUtils.parseAsJsonElement(inputJson).toString();
            String marker = Stream.iterate("__CATS_RAW_LEXEME__", token -> "_" + token)
                    .filter(token -> !canonical.contains(token))
                    .findFirst()
                    .orElseThrow();
            String quotedMarker = JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(marker);
            String templated = CatsUtil.justReplaceField(canonical, selectedField, marker).json();
            return templated.replace(quotedMarker, rawLexeme);
        } catch (RuntimeException _) {
            return inputJson;
        }
    }

    @Override
    public String description() {
        return "splice exact unquoted numeric parser boundary lexemes and raw surrogate escapes into the field";
    }
}
