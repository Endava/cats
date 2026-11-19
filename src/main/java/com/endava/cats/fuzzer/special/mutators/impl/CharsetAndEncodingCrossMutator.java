package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.CrossLayerMutator;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.HttpHeaders;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;

/**
 * Pairs a {@code Content-Type} header carrying conflicting {@code charset} parameters
 * with a BOM, multi-byte UTF-8, and Best-Fit metacharacter sequence in the target body field.
 */
@Singleton
public class CharsetAndEncodingCrossMutator implements CrossLayerMutator {
    private static final List<String> CONTENT_TYPE_CHARSETS = List.of(
            "application/json; charset=utf-8; charset=utf-16be",
            "application/json; charset=iso-8859-1; charset=utf-8",
            "application/json; charset*=UTF-8''utf-8; charset=ibm500");
    private static final List<String> ENCODING_PREFIXES = List.of(
            "\uFEFF\uFDFA\u0130\u00DF",
            "\uFF07\uFE64\u00A5\u2024\u2024\uFF0F",
            "\u212A\u017F\uFB01\u3395");

    @Override
    public String mutate(String inputJson, String selectedField) {
        Object existing = JsonUtils.getVariableFromJson(inputJson, selectedField);
        String prefix = ENCODING_PREFIXES.get(CatsRandom.instance().nextInt(ENCODING_PREFIXES.size()));
        String suffix = existing == null || JsonUtils.isNotSet(String.valueOf(existing)) ? "cats" : String.valueOf(existing);
        return CatsUtil.justReplaceField(inputJson, selectedField, prefix + suffix).json();
    }

    @Override
    public Collection<CatsHeader> mutate(Collection<CatsHeader> headers) {
        String contentType = CONTENT_TYPE_CHARSETS.get(CatsRandom.instance().nextInt(CONTENT_TYPE_CHARSETS.size()));
        return CrossLayerMutator.replaceHeader(headers, HttpHeaders.CONTENT_TYPE, contentType);
    }

    @Override
    public String description() {
        return "pair conflicting Content-Type charset parameters with multi-byte BOM and NFKC field values";
    }
}
