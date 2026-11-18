package com.endava.cats.fuzzer.fields;

import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;

import java.util.stream.Stream;

/** Replaces only the selected field's marker, preserving a number token's exact spelling without parsing it. */
record RawJsonFieldTemplate(String template, String marker) {
    static RawJsonFieldTemplate of(String payload, String field) {
        if ("$".equals(field)) {
            String root = JsonUtils.GSON_NO_PRETTY_PRINTING.toJson("__CATS_RAW_NUMBER__");
            return new RawJsonFieldTemplate(root, root);
        }
        String canonical = JsonUtils.parseAsJsonElement(payload).toString();
        String token = Stream.iterate("__CATS_RAW_NUMBER__", value -> "_" + value)
                .filter(value -> !canonical.contains(value)).findFirst().orElseThrow();
        String quoted = JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(token);
        String template = CatsUtil.justReplaceField(canonical, field, token).json();
        return new RawJsonFieldTemplate(template, quoted);
    }

    String replaceWith(String rawValue) {
        return template.replace(marker, rawValue);
    }
}
