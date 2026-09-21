package com.endava.cats.model;

import com.endava.cats.util.external.MediaType;

public enum PayloadFormat {
    JSON,
    FORM,
    TEXT,
    NDJSON,
    UNKNOWN;

    public static PayloadFormat from(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return UNKNOWN;
        }
        try {
            MediaType mediaType = MediaType.parse(contentType);
            String type = mediaType.type();
            String subtype = mediaType.subtype();
            if ("application".equals(type) && ("x-ndjson".equals(subtype) || "ndjson".equals(subtype))) {
                return NDJSON;
            }
            if ("application".equals(type) && ("json".equals(subtype) || subtype.endsWith("+json"))) {
                return JSON;
            }
            if ("application".equals(type) && "x-www-form-urlencoded".equals(subtype)) {
                return FORM;
            }
            if ("text".equals(type) && "plain".equals(subtype)) {
                return TEXT;
            }
            return UNKNOWN;
        } catch (IllegalArgumentException _) {
            return UNKNOWN;
        }
    }

    public boolean supportsNamedFields() {
        return this == JSON || this == FORM;
    }
}
