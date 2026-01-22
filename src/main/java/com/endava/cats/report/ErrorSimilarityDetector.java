package com.endava.cats.report;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.regex.Pattern;

/**
 * Utility class for detecting similarity between error messages.
 * <p>
 * Functionality preserved:
 * - Same thresholds (LEVENSHTEIN_THRESHOLD, JACCARD_THRESHOLD, PATTERN_MATCH_BONUS)
 * - Same combined scoring logic
 * - Same "same pattern" bonus semantics
 * - Same normalization intent (timestamps, UUIDs, hashes, URLs, paths, numbers)
 * <p>
 * Performance improvements:
 * - Precompiled regex Patterns (avoids String#replaceAll hot path)
 * - Reused LevenshteinDistance and JaccardSimilarity instances
 * - Normalization performed only when required for samePattern check
 */
public final class ErrorSimilarityDetector {
    private static final double LEVENSHTEIN_THRESHOLD = 0.85;
    private static final double JACCARD_THRESHOLD = 0.7;
    private static final double PATTERN_MATCH_BONUS = 0.2;
    private static final int COMPARE_PREFIX_LENGTH = 200;

    // Reuse instances instead of creating for every comparison
    private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();
    private static final JaccardSimilarity JACCARD = new JaccardSimilarity();

    private static final Pattern TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{32,}");
    private static final Pattern URL = Pattern.compile("(file|https?|ftp)://\\S+");
    private static final Pattern PATH = Pattern.compile("/[\\w/.-]+");
    private static final Pattern NUM = Pattern.compile("\\b\\d+\\b");

    private ErrorSimilarityDetector() {
        // Prevent instantiation
    }

    /**
     * Compares two error messages and returns true if they are similar enough.
     */
    public static boolean areErrorsSimilar(String error1, String error2) {
        if (StringUtils.isBlank(error1) || StringUtils.isBlank(error2)) {
            return false;
        }

        // Cheap fast-path; original logic would also return true for identical strings.
        if (error1.equals(error2)) {
            return true;
        }

        String prefix1 = prefix(error1);
        String prefix2 = prefix(error2);

        // Levenshtein similarity (expensive, but preserved)
        int distance = LEVENSHTEIN.apply(prefix1, prefix2);
        int maxLength = Math.max(prefix1.length(), prefix2.length());
        // maxLength can't be 0 here due to blank check, but keep safe.
        double levenshteinSimilarity = maxLength == 0 ? 1.0 : 1.0 - ((double) distance / (double) maxLength);

        // Jaccard similarity (preserved semantics)
        double tokenSimilarity = JACCARD.apply(prefix1, prefix2);

        // Normalization + samePattern (regex-heavy; now uses precompiled patterns)
        String pattern1 = normalizeErrorMessage(prefix1);
        String pattern2 = normalizeErrorMessage(prefix2);
        boolean samePattern = pattern1.equals(pattern2);

        double combinedScore = (levenshteinSimilarity + tokenSimilarity) / 2.0;
        if (samePattern) {
            combinedScore += PATTERN_MATCH_BONUS;
        }

        return combinedScore >= LEVENSHTEIN_THRESHOLD
                || (tokenSimilarity >= JACCARD_THRESHOLD && samePattern);
    }

    static String normalizeErrorMessage(String message) {
        // Keeping same order to preserve behavior.
        String normalized = message;
        normalized = TIMESTAMP.matcher(normalized).replaceAll("TIMESTAMP");
        normalized = UUID.matcher(normalized).replaceAll("UUID");
        normalized = HASH.matcher(normalized).replaceAll("HASH");
        normalized = URL.matcher(normalized).replaceAll("URL");
        normalized = PATH.matcher(normalized).replaceAll("PATH");
        normalized = NUM.matcher(normalized).replaceAll("NUM");
        return normalized;
    }

    private static String prefix(String s) {
        return s.length() <= COMPARE_PREFIX_LENGTH
                ? s
                : s.substring(0, COMPARE_PREFIX_LENGTH);
    }
}