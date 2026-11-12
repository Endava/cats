package com.endava.cats.report;

import com.endava.cats.model.RequestVocabulary;
import com.endava.cats.util.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns an error response into an {@link ErrorSignature}: extracts the root cause, masks request data echoed back
 * by the server ({@code <INPUT>} for sent values, {@code <FIELD>} for sent field names), applies the generic
 * variable masks (timestamps, UUIDs, hashes, URLs, paths, numbers, single-quoted strings, bracketed lists) and
 * splits the result into tokens.
 * <p>
 * Double-quoted strings are deliberately not masked: in exception messages such as
 * {@code Cannot invoke "String.length()" because "val" is null} they name the failing code and are what tells
 * two crashes apart.
 */
public final class ErrorSignatureMasker {
    static final String EMPTY_TYPE = "EMPTY";
    static final String INPUT = "<INPUT>";
    static final String FIELD = "<FIELD>";
    private static final int MAX_TOKENS = 40;
    private static final int MIN_FIELD_NAME_LENGTH = 2;
    private static final Set<String> MESSAGE_WORDS = Set.of("a", "an", "and", "are", "as", "at", "be", "by", "can",
            "for", "from", "has", "if", "in", "into", "is", "it", "no", "not", "of", "on", "or", "the", "to", "was",
            "with", "value", "values", "type", "field", "fields", "parameter", "request", "input", "string", "number",
            "object", "array", "null", "error", "message", "body", "data", "length", "size", "format");
    private static final List<String> TRUNCATED_JSON_TYPE_KEYS = List.of("title", "error", "exception", "errorCode", "code", "type");
    private static final List<String> TRUNCATED_JSON_DETAIL_KEYS = List.of("detail", "message", "description", "error_description", "reason");
    private static final Pattern JSON_STRING_FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final int MIN_FRAGMENT_LENGTH = 3;
    private static final Pattern DOUBLE_QUOTED = Pattern.compile("\"([^\"]{1,200})\"");
    private static final Pattern SINGLE_QUOTED = Pattern.compile("'([^']{1,200})'");
    private static final Pattern BRACKETED = Pattern.compile("\\[[^\\[\\]]{1,200}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern TOKEN_CORE = Pattern.compile("(\\W*)([\\w.]+?)(\\W*)");
    private static final Comparator<String> LONGEST_FIRST = Comparator.comparingInt(String::length).reversed()
            .thenComparing(Comparator.naturalOrder());

    private ErrorSignatureMasker() {
    }

    /**
     * Builds the signature of an error response.
     *
     * @param responseBody    the response body
     * @param fallbackDetails text used when the body is empty, or {@code null} to group all empty bodies together
     * @param vocabulary      the vocabulary of the request that produced the response
     * @return the masked signature
     */
    public static ErrorSignature signatureOf(String responseBody, String fallbackDetails, RequestVocabulary vocabulary) {
        if (ErrorSimilarityDetector.isBlankOrEmptyPlaceholder(responseBody)) {
            return new ErrorSignature(EMPTY_TYPE, tokens(StringUtils.defaultString(fallbackDetails), vocabulary), "");
        }
        ErrorSimilarityDetector.ExtractedCause cause = extractCause(responseBody);
        return new ErrorSignature(maskErrorType(cause.errorType(), vocabulary), tokens(cause.detail(), vocabulary),
                cause.stackFrame());
    }

    /**
     * Error types are partition keys, so variable parts must be masked (e.g. {@code {"error": "User 123 not found"}}),
     * but numeric error codes such as {@code 404} or {@code 1001} identify the error and are kept.
     */
    private static String maskErrorType(String errorType, RequestVocabulary vocabulary) {
        return StringUtils.isNumeric(errorType) ? errorType : maskText(errorType, vocabulary).strip();
    }

    /**
     * Report summaries keep only the first 1000 characters of a body, which often cuts a JSON error object in half.
     * Recover the error type and detail from the string fields that survived instead of treating the body as raw text.
     */
    private static ErrorSimilarityDetector.ExtractedCause extractCause(String responseBody) {
        ErrorSimilarityDetector.ExtractedCause cause = ErrorSimilarityDetector.extractCause(responseBody);
        String trimmed = responseBody.strip();
        if (!cause.errorType().isEmpty() || !trimmed.startsWith("{") || JsonUtils.isValidJson(trimmed)) {
            return cause;
        }
        Map<String, String> fields = JSON_STRING_FIELD.matcher(trimmed).results()
                .collect(Collectors.toMap(match -> match.group(1), match -> unescapeQuietly(match.group(2)), (first, _) -> first));
        String errorType = firstPresent(fields, TRUNCATED_JSON_TYPE_KEYS);
        String detail = firstPresent(fields, TRUNCATED_JSON_DETAIL_KEYS);
        return errorType.isEmpty() && detail.isEmpty() ? cause
                : new ErrorSimilarityDetector.ExtractedCause(errorType, detail, cause.stackFrame());
    }

    private static String unescapeQuietly(String jsonString) {
        try {
            return StringEscapeUtils.unescapeJson(jsonString);
        } catch (IllegalArgumentException _) {
            return jsonString;
        }
    }

    private static String firstPresent(Map<String, String> fields, List<String> keys) {
        return keys.stream().map(fields::get).filter(StringUtils::isNotBlank).findFirst().orElse("");
    }

    /**
     * Applies the value and generic masks to free text, without tokenizing it.
     *
     * @param text       the text to mask
     * @param vocabulary the vocabulary of the request whose echoed values should be masked
     * @return the masked text
     */
    static String maskText(String text, RequestVocabulary vocabulary) {
        String withoutInput = maskQuotedFragments(maskSentValues(text, vocabulary.values()), vocabulary.values());
        String normalized = ErrorSimilarityDetector.normalizeForClustering(withoutInput);
        return BRACKETED.matcher(normalized).replaceAll("[LIST]");
    }

    /**
     * Masks quoted text that is part of a sent value: servers often echo a transformed fragment of the input, e.g.
     * {@code For input string: "a14f5"} for a sent {@code 0xa14f5}.
     */
    private static String maskQuotedFragments(String text, Set<String> values) {
        if (values.isEmpty()) {
            return text;
        }
        return maskQuotedFragments(maskQuotedFragments(text, values, DOUBLE_QUOTED, "\""), values, SINGLE_QUOTED, "'");
    }

    private static String maskQuotedFragments(String text, Set<String> values, Pattern quoted, String quote) {
        return quoted.matcher(text).replaceAll(match -> Matcher.quoteReplacement(
                isSentFragment(match.group(1), values) ? quote + INPUT + quote : match.group()));
    }

    private static boolean isSentFragment(String quoted, Set<String> values) {
        return quoted.strip().length() >= MIN_FRAGMENT_LENGTH && values.stream().anyMatch(value -> value.contains(quoted));
    }

    static List<String> tokens(String text, RequestVocabulary vocabulary) {
        return WHITESPACE.splitAsStream(maskText(text, vocabulary).strip())
                .filter(StringUtils::isNotEmpty)
                .map(token -> maskFieldName(token, vocabulary.fieldNames()))
                .limit(MAX_TOKENS)
                .toList();
    }

    /**
     * Masks sent values the generic masks would leave untouched. Values the generic masks already recognize (numbers,
     * UUIDs, timestamps...) are left to them, so the same message gets the same template whether or not the value
     * happened to be sent.
     */
    private static String maskSentValues(String text, Set<String> values) {
        String masked = text;
        for (String value : values.stream().filter(ErrorSignatureMasker::isFreeText).sorted(LONGEST_FIRST).toList()) {
            masked = maskValue(masked, value);
        }
        return masked;
    }

    private static boolean isFreeText(String value) {
        return ErrorSimilarityDetector.normalizeForClustering(value).equals(value);
    }

    private static String maskValue(String text, String value) {
        if (!text.contains(value)) {
            return text;
        }
        Pattern standalone = Pattern.compile("(?<!\\w)" + Pattern.quote(value) + "(?!\\w)");
        return standalone.matcher(text).replaceAll(Matcher.quoteReplacement(INPUT));
    }

    private static String maskFieldName(String token, Set<String> fieldNames) {
        Matcher matcher = TOKEN_CORE.matcher(token);
        if (fieldNames.isEmpty() || !matcher.matches() || !isFieldName(matcher.group(2), fieldNames)) {
            return token;
        }
        return matcher.group(1) + FIELD + matcher.group(3);
    }

    private static boolean isFieldName(String core, Set<String> fieldNames) {
        return core.length() >= MIN_FIELD_NAME_LENGTH && !MESSAGE_WORDS.contains(core.toLowerCase(Locale.ROOT))
                && (fieldNames.contains(core) || fieldNames.contains(StringUtils.substringAfterLast(core, ".")));
    }
}
