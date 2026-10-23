package com.endava.cats.util;

import com.endava.cats.util.external.MediaType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shared utility methods for matching, comparing, and negotiating HTTP {@code Content-Type} and {@code Accept} media types.
 */
public abstract class ContentTypeUtils {
    private static final String UNKNOWN_MEDIA_TYPE = "unknown/unknown";

    private ContentTypeUtils() {
        // utility class
    }

    /**
     * Checks whether an actual media type declared in an OpenAPI contract matches a configured media type or regex pattern.
     *
     * @param actual     the media type key from the OpenAPI contract
     * @param configured the configured media type or regex pattern (such as {@link JsonUtils#JSON_WILDCARD})
     * @return {@code true} if {@code actual} matches {@code configured}
     */
    public static boolean matchesContentType(String actual, String configured) {
        if (actual == null || configured == null) {
            return false;
        }
        if (actual.equalsIgnoreCase(configured)) {
            return true;
        }
        try {
            if (MediaType.parse(actual).withoutParameters().is(MediaType.parse(configured).withoutParameters())) {
                return true;
            }
        } catch (IllegalArgumentException _) {
            return matchesContentTypePattern(actual, configured);
        }
        return matchesContentTypePattern(actual, configured);
    }

    private static boolean matchesContentTypePattern(String actual, String configured) {
        try {
            return actual.matches("(?i:" + configured + ")");
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    /**
     * Checks whether two media types are equivalent (ignoring parameters and allowing structured {@code +json} subtypes
     * to match their base subtype when comparing contract vs. response media types).
     *
     * @param firstContentType  first media type
     * @param secondContentType second media type
     * @return {@code true} if the two media types are equivalent
     */
    public static boolean areContentTypesEquivalent(String firstContentType, String secondContentType) {
        try {
            MediaType firstMediaType = MediaType.parse(Optional.ofNullable(firstContentType).orElse(UNKNOWN_MEDIA_TYPE)).withoutParameters();
            MediaType secondMediaType = MediaType.parse(Optional.ofNullable(secondContentType).orElse(UNKNOWN_MEDIA_TYPE)).withoutParameters();

            return firstMediaType.is(secondMediaType)
                    || secondMediaType.is(firstMediaType)
                    || (firstMediaType.type().equalsIgnoreCase(secondMediaType.type())
                    && (firstMediaType.subtype().endsWith(secondMediaType.subtype())
                    || secondMediaType.subtype().endsWith(firstMediaType.subtype())));
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    /**
     * Checks whether the given media type is plain {@code application/json} (with optional parameters) or a wildcard {@code *&#47;*}.
     *
     * @param mediaType the media type to inspect
     * @return {@code true} if plain JSON or wildcard
     */
    public static boolean isPlainJsonOrWildcard(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return false;
        }
        String normalized = mediaType.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("application/json") || normalized.contains("*/*");
    }

    /**
     * Resolves the {@code Accept} header value for an operation from its declared {@code 2XX} response content types.
     * Returns {@code null} when the operation supports plain {@code application/json} or {@code *&#47;*} so the caller's
     * default/configured content type is used; otherwise returns the first declared {@code 2XX} media type
     * (such as {@code application/hal+json}, {@code application/vnd.api+json}, or {@code application/xml}).
     *
     * @param responseContentTypes map of response code to declared media types
     * @return specific {@code Accept} media type, or {@code null} to use the default
     */
    public static String resolveAcceptHeader(Map<String, List<String>> responseContentTypes) {
        if (responseContentTypes == null || responseContentTypes.isEmpty()) {
            return null;
        }
        List<String> candidateMediaTypes = responseContentTypes.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith("2"))
                .flatMap(entry -> entry.getValue().stream())
                .filter(mt -> mt != null && !mt.isBlank())
                .toList();
        if (candidateMediaTypes.isEmpty()) {
            candidateMediaTypes = responseContentTypes.values().stream()
                    .filter(java.util.Objects::nonNull)
                    .flatMap(List::stream)
                    .filter(mt -> mt != null && !mt.isBlank())
                    .toList();
        }
        if (candidateMediaTypes.isEmpty()) {
            return null;
        }
        boolean supportsPlainJsonOrWildcard = candidateMediaTypes.stream()
                .anyMatch(ContentTypeUtils::isPlainJsonOrWildcard);
        return supportsPlainJsonOrWildcard ? null : candidateMediaTypes.getFirst();
    }
}
