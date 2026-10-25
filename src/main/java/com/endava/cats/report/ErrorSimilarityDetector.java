package com.endava.cats.report;

import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for extracting root-cause signatures and detecting similarity between error responses.
 */
public final class ErrorSimilarityDetector {
    private static final double LEVENSHTEIN_THRESHOLD = 0.85;
    private static final double JACCARD_THRESHOLD = 0.7;
    private static final double PATTERN_MATCH_BONUS = 0.2;
    private static final int COMPARE_PREFIX_LENGTH = 240;

    private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();
    private static final JaccardSimilarity JACCARD = new JaccardSimilarity();

    private static final Pattern TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[0-9.Z+-:]*");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{32,}");
    private static final Pattern URL = Pattern.compile("(file|https?|ftp)://\\S+");
    private static final Pattern PATH = Pattern.compile("/[\\w/.-]+");
    private static final Pattern NUM = Pattern.compile("-?\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern SINGLE_QUOTED = Pattern.compile("'[^']{1,120}'");
    private static final Pattern STACK_FRAME = Pattern.compile("at\\s+([a-zA-Z0-9_.$]+\\([^)]+\\))");
    private static final Pattern WORD_SPLIT = Pattern.compile("[^a-zA-Z0-9_]+");

    private static final List<String> ERROR_TYPE_KEYS = List.of(
            "exception", "errorCode", "code", "title", "error", "type");
    private static final List<String> ERROR_DETAIL_KEYS = List.of(
            "detail", "message", "description", "error_description", "reason", "faultstring");
    private static final List<String> ERROR_ARRAY_KEYS = List.of(
            "errors", "violations", "details", "fieldErrors", "causes");
    private static final List<String> STACK_KEYS = List.of(
            "trace", "stackTrace", "stack", "exceptionTrace");

    private ErrorSimilarityDetector() {
        // Prevent instantiation
    }

    /**
     * Compares two error messages and returns true if they represent the same underlying root cause.
     */
    public static boolean areErrorsSimilar(String error1, String error2) {
        if (StringUtils.isBlank(error1) || StringUtils.isBlank(error2)) {
            return false;
        }
        if (error1.equals(error2)) {
            return true;
        }

        ExtractedCause cause1 = extractCause(error1);
        ExtractedCause cause2 = extractCause(error2);
        if (!cause1.errorType().isEmpty() && !cause2.errorType().isEmpty() &&
                !cause1.errorType().equalsIgnoreCase(cause2.errorType())) {
            return false;
        }

        String prefix1 = prefix(cause1.signatureText());
        String prefix2 = prefix(cause2.signatureText());

        int distance = LEVENSHTEIN.apply(prefix1, prefix2);
        int maxLength = Math.max(prefix1.length(), prefix2.length());
        double levenshteinSimilarity = maxLength == 0 ? 1.0 : 1.0 - ((double) distance / (double) maxLength);

        double charJaccard = JACCARD.apply(prefix1, prefix2);
        double wordJaccard = wordTokenJaccard(prefix1, prefix2);
        double tokenSimilarity = Math.max(charJaccard, wordJaccard);

        String pattern1 = normalizeForClustering(prefix1);
        String pattern2 = normalizeForClustering(prefix2);
        boolean samePattern = pattern1.equals(pattern2);

        double combinedScore = (levenshteinSimilarity + tokenSimilarity) / 2.0;
        if (samePattern) {
            combinedScore += PATTERN_MATCH_BONUS;
        }

        return combinedScore >= LEVENSHTEIN_THRESHOLD
                || (tokenSimilarity >= JACCARD_THRESHOLD && samePattern);
    }

    /**
     * Extracts a concise, human-readable 1-line root-cause summary from a response body and result details.
     */
    public static String extractRootCauseSummary(String responseBody, String fallbackDetails) {
        if (isBlankOrEmptyPlaceholder(responseBody)) {
            return StringUtils.isNotBlank(fallbackDetails)
                    ? truncate(singleLine(fallbackDetails), 160)
                    : "[Empty response body]";
        }
        ExtractedCause cause = extractCause(responseBody);
        StringBuilder summary = new StringBuilder();
        if (!cause.errorType().isEmpty()) {
            summary.append(cause.errorType());
        }
        if (!cause.detail().isEmpty() && !cause.detail().equalsIgnoreCase(cause.errorType())) {
            if (!summary.isEmpty()) {
                summary.append(": ");
            }
            summary.append(cause.detail());
        }
        if (!cause.stackFrame().isEmpty()) {
            summary.append(" @ ").append(cause.stackFrame());
        }
        String text = singleLine(summary.isEmpty() ? responseBody : summary.toString());
        return truncate(text, 160);
    }

    /**
     * Computes a canonical normalized pattern key for fast first-pass clustering.
     */
    public static String extractNormalizedClusterKey(String responseBody, String fallbackDetails) {
        if (isBlankOrEmptyPlaceholder(responseBody)) {
            return "EMPTY|" + normalizeForClustering(prefix(optionalString(fallbackDetails)));
        }
        ExtractedCause cause = extractCause(responseBody);
        return cause.errorType().toLowerCase(Locale.ROOT) + "|"
                + normalizeForClustering(prefix(cause.signatureText())) + "|"
                + cause.stackFrame();
    }

    static boolean isBlankOrEmptyPlaceholder(String responseBody) {
        return StringUtils.isBlank(responseBody) || "[Empty response body]".equalsIgnoreCase(responseBody.trim());
    }

    static ExtractedCause extractCause(String rawBody) {
        if (isBlankOrEmptyPlaceholder(rawBody)) {
            return new ExtractedCause("", "", "");
        }
        String trimmed = rawBody.trim();
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && JsonUtils.isValidJson(trimmed)) {
            try {
                JsonElement root = JsonUtils.parseAsJsonElement(trimmed);
                if (root.isJsonArray() && !root.getAsJsonArray().isEmpty() && root.getAsJsonArray().get(0).isJsonObject()) {
                    root = root.getAsJsonArray().get(0);
                }
                if (root.isJsonObject()) {
                    return extractFromJsonObject(root.getAsJsonObject(), trimmed);
                }
            } catch (RuntimeException _) {
                // Fall back to raw text when JSON structure is non-standard.
            }
        }
        return new ExtractedCause("", trimmed, extractFirstStackFrame(trimmed));
    }

    private static ExtractedCause extractFromJsonObject(JsonObject obj, String fallbackRaw) {
        String errorType = firstStringProperty(obj, ERROR_TYPE_KEYS);
        if (errorType.startsWith("http://") || errorType.startsWith("https://") || "about:blank".equalsIgnoreCase(errorType)) {
            String title = firstStringProperty(obj, List.of("title", "error", "code", "errorCode"));
            if (!title.isEmpty()) {
                errorType = title;
            }
        }
        String detail = firstStringProperty(obj, ERROR_DETAIL_KEYS);
        if (detail.isEmpty()) {
            detail = firstArrayErrorSummary(obj);
        }
        String stack = firstStringProperty(obj, STACK_KEYS);
        String stackFrame = extractFirstStackFrame(stack.isEmpty() ? detail : stack);
        if (errorType.isEmpty() && detail.isEmpty()) {
            return new ExtractedCause("", fallbackRaw, stackFrame);
        }
        return new ExtractedCause(errorType, detail, stackFrame);
    }

    private static String firstStringProperty(JsonObject obj, List<String> candidateKeys) {
        for (String key : candidateKeys) {
            JsonElement el = obj.get(key);
            if (el != null && el.isJsonPrimitive()) {
                String val = el.getAsString().trim();
                if (!val.isEmpty()) {
                    return val;
                }
            } else if (el != null && el.isJsonObject()) {
                String nested = firstStringProperty(el.getAsJsonObject(), List.of("code", "type", "message", "detail"));
                if (!nested.isEmpty()) {
                    return nested;
                }
            }
        }
        return "";
    }

    private static String firstArrayErrorSummary(JsonObject obj) {
        for (String key : ERROR_ARRAY_KEYS) {
            JsonElement el = obj.get(key);
            if (el != null && el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                if (!arr.isEmpty()) {
                    JsonElement first = arr.get(0);
                    if (first.isJsonPrimitive()) {
                        return first.getAsString().trim();
                    }
                    if (first.isJsonObject()) {
                        return firstStringProperty(first.getAsJsonObject(),
                                List.of("message", "detail", "defaultMessage", "reason", "code", "field"));
                    }
                }
            }
        }
        return "";
    }

    private static String extractFirstStackFrame(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        Matcher matcher = STACK_FRAME.matcher(text);
        while (matcher.find()) {
            String frame = matcher.group(1);
            if (!frame.startsWith("java.") && !frame.startsWith("jdk.") && !frame.startsWith("sun.")) {
                return frame;
            }
        }
        return "";
    }

    static String normalizeErrorMessage(String message) {
        String normalized = message;
        normalized = TIMESTAMP.matcher(normalized).replaceAll("TIMESTAMP");
        normalized = UUID.matcher(normalized).replaceAll("UUID");
        normalized = HASH.matcher(normalized).replaceAll("HASH");
        normalized = URL.matcher(normalized).replaceAll("URL");
        normalized = PATH.matcher(normalized).replaceAll("PATH");
        normalized = NUM.matcher(normalized).replaceAll("NUM");
        return normalized;
    }

    private static String normalizeForClustering(String message) {
        String normalized = normalizeErrorMessage(message);
        return SINGLE_QUOTED.matcher(normalized).replaceAll("'STR'");
    }

    static double wordTokenJaccard(String s1, String s2) {
        Set<String> tokens1 = tokenizeWords(normalizeForClustering(s1));
        Set<String> tokens2 = tokenizeWords(normalizeForClustering(s2));
        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            return 1.0;
        }
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String t : tokens1) {
            if (tokens2.contains(t)) {
                intersection++;
            }
        }
        int union = tokens1.size() + tokens2.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static Set<String> tokenizeWords(String text) {
        Set<String> tokens = new HashSet<>();
        Arrays.stream(WORD_SPLIT.split(text.toLowerCase(Locale.ROOT)))
                .filter(tok -> tok.length() >= 2)
                .forEach(tokens::add);
        return tokens;
    }

    private static String prefix(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= COMPARE_PREFIX_LENGTH ? s : s.substring(0, COMPARE_PREFIX_LENGTH);
    }

    private static String singleLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }

    private static String optionalString(String value) {
        return value == null ? "" : value;
    }

    record ExtractedCause(String errorType, String detail, String stackFrame) {
        String signatureText() {
            if (errorType.isEmpty()) {
                return detail;
            }
            if (detail.isEmpty()) {
                return errorType;
            }
            return errorType + ": " + detail;
        }
    }
}
