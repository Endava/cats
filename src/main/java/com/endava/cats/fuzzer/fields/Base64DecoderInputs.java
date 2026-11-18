package com.endava.cats.fuzzer.fields;

import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.generator.simple.Base64BoundaryGenerator;
import com.endava.cats.util.WordUtils;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Set;

/** Shared, disjoint inputs for cursor structure checks and Base64 decoder boundary checks. */
final class Base64DecoderInputs {
    private static final Set<String> CURSOR_FIELD_NAMES = Set.of(
            "cursor", "nextcursor", "prevcursor", "previouscursor",
            "pagetoken", "nextpagetoken", "prevpagetoken", "continuationtoken",
            "startingafter", "endingbefore", "after", "before", "scrollid", "searchafter");
    private static final List<Object> CURSOR_VALUES = List.of(
            "e30=", "bnVsbA==", "bm90LWEtdGltZXN0YW1wOm5vdC1hbi1pZA==", "===invalid-cursor===");
    private static final List<Object> BOUNDARY_VALUES = Base64BoundaryGenerator.values().stream().map(value -> (Object) value).toList();

    private Base64DecoderInputs() {
    }

    static boolean isCursorField(String field) {
        return CURSOR_FIELD_NAMES.contains(WordUtils.normalizeLeaf(field));
    }

    static boolean appliesTo(Schema<?> schema, String field) {
        return schema != null && CatsModelUtils.isStringSchema(schema) && !CatsModelUtils.isEnumSchema(schema)
                && (CatsModelUtils.isByteArraySchema(schema) || hasBase64Encoding(schema) || isCursorField(field));
    }

    private static boolean hasBase64Encoding(Schema<?> schema) {
        return "base64".equalsIgnoreCase(schema.getContentEncoding()) || "base64url".equalsIgnoreCase(schema.getContentEncoding());
    }

    static List<Object> cursorValues() {
        return CURSOR_VALUES;
    }

    static List<Object> boundaryValues() {
        return BOUNDARY_VALUES;
    }

}
