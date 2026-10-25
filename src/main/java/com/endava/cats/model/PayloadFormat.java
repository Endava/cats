package com.endava.cats.model;

import com.endava.cats.util.external.MediaType;

public enum PayloadFormat {
    JSON,
    FORM,
    XML,
    TEXT,
    NDJSON,
    UNKNOWN;

    public static PayloadFormat from(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return UNKNOWN;
        }
        try {
            MediaType mediaType = MediaType.parse(contentType);
            return classifyMediaType(mediaType.type(), mediaType.subtype());
        } catch (IllegalArgumentException _) {
            return UNKNOWN;
        }
    }

    private static PayloadFormat classifyMediaType(String type, String subtype) {
        if (isXmlSubtype(type, subtype)) {
            return XML;
        }
        if ("application".equals(type)) {
            return classifyApplicationSubtype(subtype);
        }
        return ("text".equals(type) && "plain".equals(subtype)) ? TEXT : UNKNOWN;
    }

    private static PayloadFormat classifyApplicationSubtype(String subtype) {
        if ("x-ndjson".equals(subtype) || "ndjson".equals(subtype)) {
            return NDJSON;
        }
        if ("json".equals(subtype) || subtype.endsWith("+json")) {
            return JSON;
        }
        return "x-www-form-urlencoded".equals(subtype) ? FORM : UNKNOWN;
    }

    private static boolean isXmlSubtype(String type, String subtype) {
        return subtype.endsWith("+xml")
                || ("xml".equals(subtype) && ("application".equals(type) || "text".equals(type)));
    }

    public boolean supportsNamedFields() {
        return this == JSON || this == FORM || this == XML;
    }
}
