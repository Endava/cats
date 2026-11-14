package com.endava.cats.report.error.mask;

import java.util.regex.Pattern;

/**
 * The generic masks of error template mining: timestamps, UUIDs, hashes, URLs, paths and numbers, plus the quote
 * policy of {@link QuotedFragments}.
 */
public final class GenericMasks {
    private static final Pattern TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[0-9.Z+:-]*");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{32,}");
    private static final Pattern URL = Pattern.compile("(file|https?|ftp)://\\S+");
    private static final Pattern PATH = Pattern.compile("/[\\w/.-]+");
    private static final Pattern NUM = Pattern.compile("-?\\b\\d+(?:\\.\\d+)?\\b");

    private GenericMasks() {
    }

    /**
     * Replaces timestamps, UUIDs, hashes, URLs, paths and numbers by placeholders.
     *
     * @param message the message to mask
     * @return the masked message
     */
    public static String normalizeErrorMessage(String message) {
        String normalized = message;
        normalized = TIMESTAMP.matcher(normalized).replaceAll("TIMESTAMP");
        normalized = UUID.matcher(normalized).replaceAll("UUID");
        normalized = HASH.matcher(normalized).replaceAll("HASH");
        normalized = URL.matcher(normalized).replaceAll("URL");
        normalized = PATH.matcher(normalized).replaceAll("PATH");
        normalized = NUM.matcher(normalized).replaceAll("NUM");
        return normalized;
    }

    /**
     * Applies the generic variable masks and the quote policy of {@link QuotedFragments}.
     *
     * @param message the message to mask
     * @return the masked message
     */
    public static String normalizeForClustering(String message) {
        return QuotedFragments.maskData(normalizeErrorMessage(message));
    }
}
