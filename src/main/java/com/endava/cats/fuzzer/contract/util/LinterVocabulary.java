package com.endava.cats.fuzzer.contract.util;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

public abstract class LinterVocabulary {
    public static final Set<String> BODYLESS_STATUS_CODES = Set.of(
            "204", "205", "301", "302", "303", "304", "307", "308"
    );

    public static final Set<String> LOCATION_ALLOWED_BODYLESS_CODES = Set.of("201", "202");

    public static final Set<String> PAGINATION_PARAMS = Set.of(
            "limit", "offset", "page", "perpage", "pagesize", "pagenumber", "pagetoken",
            "size", "cursor", "startafter", "after", "before", "next", "nexttoken",
            "continuationtoken", "token", "skip", "take", "top"
    );

    public static final Set<String> SINGLETON_SEGMENTS = Set.of(
            "me", "self", "current", "context", "profile", "health", "healthz",
            "healthcheck", "health-check", "ready", "readyz", "live", "livez",
            "ping", "info", "version", "config", "configuration", "metadata", "status"
    );

    public static final Set<String> UNCOUNTABLE_SEGMENTS = Set.of(
            "evidence", "information", "metadata", "data", "branding", "feedback",
            "equipment", "traffic", "software", "hardware", "advice"
    );

    private LinterVocabulary() {
    }

    public static boolean isBodylessStatus(String code) {
        return code != null && (BODYLESS_STATUS_CODES.contains(code) || code.startsWith("1"));
    }

    public static boolean hasLocationHeader(Collection<String> headerNames) {
        return headerNames != null && headerNames.stream().anyMatch("Location"::equalsIgnoreCase);
    }

    public static boolean isBinaryOrStreamContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("image/")
                || lower.startsWith("audio/")
                || lower.startsWith("video/")
                || "application/octet-stream".equals(lower)
                || "application/pdf".equals(lower)
                || "text/event-stream".equals(lower);
    }

    public static String normalizeParamName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[-_]", "").toLowerCase(Locale.ROOT);
    }
}
