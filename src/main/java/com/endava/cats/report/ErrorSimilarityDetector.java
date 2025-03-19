package com.endava.cats.report;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;

/**
 * Utility class for detecting similarity between error messages.
 */
public class ErrorSimilarityDetector {
    private static final double LEVENSHTEIN_THRESHOLD = 0.85;
    private static final double JACCARD_THRESHOLD = 0.7;
    private static final double PATTERN_MATCH_BONUS = 0.2;

    /**
     * Compares two error messages and returns true if they are similar enough.
     *
     * @param error1 the first error message
     * @param error2 the second error message
     * @return true if the error messages are similar, false otherwise
     */
    public static boolean areErrorsSimilar(String error1, String error2) {
        if (StringUtils.isBlank(error1) || StringUtils.isBlank(error2)) {
            return false;
        }

        int distance = LevenshteinDistance.getDefaultInstance().apply(error1, error2);
        int maxLength = Math.max(error1.length(), error2.length());
        double levenshteinSimilarity = 1.0 - ((double) distance / maxLength);

        JaccardSimilarity jaccardSimilarity = new JaccardSimilarity();
        double tokenSimilarity = jaccardSimilarity.apply(error1, error2);

        String pattern1 = normalizeErrorMessage(error1);
        String pattern2 = normalizeErrorMessage(error2);
        boolean samePattern = pattern1.equals(pattern2);

        double combinedScore = (levenshteinSimilarity + tokenSimilarity) / 2;
        if (samePattern) {
            combinedScore += PATTERN_MATCH_BONUS;
        }

        return combinedScore >= LEVENSHTEIN_THRESHOLD ||
                (tokenSimilarity >= JACCARD_THRESHOLD && samePattern);
    }

    static String normalizeErrorMessage(String message) {
        String normalized = message.replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}", "TIMESTAMP");

        normalized = normalized.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "UUID");
        normalized = normalized.replaceAll("[0-9a-f]{32,}", "HASH");

        normalized = normalized.replaceAll("(file|https?|ftp)://[^\\s]+", "URL");
        normalized = normalized.replaceAll("/[\\w/.-]+", "PATH");
        normalized = normalized.replaceAll("\\b\\d+\\b", "NUM");

        return normalized;
    }
}
