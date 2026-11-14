package com.endava.cats.report.error.mask;

import com.endava.cats.model.ErrorSignature;
import com.endava.cats.model.RequestVocabulary;
import com.endava.cats.report.error.contract.ErrorResponseContract;
import com.endava.cats.report.error.extract.EmptyBody;
import com.endava.cats.report.error.extract.ErrorBodyExtraction;
import com.endava.cats.report.error.extract.ErrorCause;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns an error response into an {@link ErrorSignature}: extracts the root cause ({@link ErrorBodyExtraction}),
 * masks request data echoed back by the server ({@code <INPUT>} for sent values, {@code <FIELD>} for sent field
 * names), applies the generic variable masks (timestamps, UUIDs, hashes, URLs, paths, numbers, bracketed lists) and
 * the quote policy of {@link QuotedFragments}, and splits the result into tokens.
 * <p>
 * Quoted code identifiers are deliberately not masked: in messages such as
 * {@code Cannot invoke "String.length()" because "val" is null} or {@code (reading 'id')} they name the failing code
 * and are what tells two crashes apart.
 */
public final class ErrorSignatureMasker {
    public static final String EMPTY_TYPE = "EMPTY";
    static final String INPUT = "<INPUT>";
    static final String FIELD = "<FIELD>";
    private static final int MAX_TOKENS = 40;
    private static final int MAX_MESSAGES = 5;
    private static final Pattern GENERIC_PLACEHOLDER = Pattern.compile("NUM|UUID|HASH|URL|PATH|TIMESTAMP");
    private static final int MIN_FIELD_NAME_LENGTH = 2;
    /**
     * Words that are never masked as field names even when a request field has that name. The list is English only:
     * messages in other languages are still masked correctly, because a token is only masked when it equals a sent
     * field name, but a field named like a common word of another language (e.g. {@code nombre}, {@code wert}) is
     * masked inside its messages too.
     */
    private static final Set<String> MESSAGE_WORDS = Set.of("a", "an", "and", "are", "as", "at", "be", "by", "can",
            "for", "from", "has", "if", "in", "into", "is", "it", "no", "not", "of", "on", "or", "the", "to", "was",
            "with", "value", "values", "type", "field", "fields", "parameter", "request", "input", "string", "number",
            "object", "array", "null", "error", "message", "body", "data", "length", "size", "format");
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
        return signatureOf(responseBody, fallbackDetails, vocabulary, ErrorResponseContract.NONE);
    }

    /**
     * Builds the signature of an error response, reading JSON fields by the roles the contract declares when the body
     * matches the error schema it declares for the response.
     *
     * @param responseBody    the response body
     * @param fallbackDetails text used when the body is empty, or {@code null} to group all empty bodies together
     * @param vocabulary      the vocabulary of the request that produced the response
     * @param contract        what the OpenAPI contract declares about the error body
     * @return the masked signature
     */
    public static ErrorSignature signatureOf(String responseBody, String fallbackDetails, RequestVocabulary vocabulary,
                                      ErrorResponseContract contract) {
        if (EmptyBody.isEmpty(responseBody)) {
            return new ErrorSignature(EMPTY_TYPE, tokens(StringUtils.defaultString(fallbackDetails), vocabulary), "");
        }
        ErrorCause cause = ErrorBodyExtraction.extract(responseBody, contract);
        return new ErrorSignature(maskErrorType(cause, vocabulary), messageTokens(cause.messages(), vocabulary), cause.stackFrame());
    }

    /**
     * Error types are partition keys, so variable parts must be masked (e.g. {@code {"error": "User 123 not found"}}),
     * but numeric error codes such as {@code 404} or {@code 1001} identify the error and are kept, and so are codes
     * such as {@code USR-001} that a code or type field holds, see {@link ErrorTypeCodes}.
     */
    private static String maskErrorType(ErrorCause cause, RequestVocabulary vocabulary) {
        String errorType = cause.errorType();
        return StringUtils.isNumeric(errorType) || isKeptCode(cause, vocabulary) ? errorType : maskText(errorType, vocabulary).strip();
    }

    private static boolean isKeptCode(ErrorCause cause, RequestVocabulary vocabulary) {
        return cause.typeFromCodeField() && ErrorTypeCodes.isCode(cause.errorType()) && !vocabulary.values().contains(cause.errorType());
    }

    /**
     * Masks every message on its own, then keeps at most {@value #MAX_MESSAGES} distinct masked messages in sorted
     * order: the same validation rule gives the same template whatever the order or number of violating fields.
     */
    private static List<String> messageTokens(List<String> messages, RequestVocabulary vocabulary) {
        String masked = messages.stream().map(message -> String.join(" ", tokens(message, vocabulary)))
                .filter(StringUtils::isNotEmpty).distinct().sorted().limit(MAX_MESSAGES)
                .collect(Collectors.joining("; "));
        return WHITESPACE.splitAsStream(masked).filter(StringUtils::isNotEmpty).limit(MAX_TOKENS).toList();
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
        String normalized = GenericMasks.normalizeForClustering(withoutInput);
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

    /**
     * A fragment must cover at least half of the sent value, so a quoted word that merely occurs inside a longer value
     * (the validator tag {@code 'email'} for a sent {@code not-an-email}) is not mistaken for echoed input.
     */
    private static boolean isSentFragment(String quoted, Set<String> values) {
        return quoted.strip().length() >= MIN_FRAGMENT_LENGTH
                && values.stream().anyMatch(value -> value.contains(quoted) && 2 * quoted.length() >= value.length());
    }

    static List<String> tokens(String text, RequestVocabulary vocabulary) {
        return WHITESPACE.splitAsStream(maskText(text, vocabulary).strip())
                .filter(StringUtils::isNotEmpty)
                .map(token -> maskFieldName(token, vocabulary.fieldNames()))
                .limit(MAX_TOKENS)
                .toList();
    }

    /**
     * Masks sent values the generic masks would not fully mask. Values the generic masks already recognize (numbers,
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

    /**
     * A value the generic masks turn into a single placeholder (a number, a UUID, a timestamp...) is left to them.
     * Any other value, including attack payloads such as {@code ' OR '1'='1} that the generic masks would only
     * partially rewrite, is masked as a whole.
     */
    private static boolean isFreeText(String value) {
        return !GENERIC_PLACEHOLDER.matcher(GenericMasks.normalizeErrorMessage(value.strip())).matches();
    }

    private static String maskValue(String text, String value) {
        if (!text.contains(value)) {
            return text;
        }
        Pattern standalone = Pattern.compile("(?<!\\w)" + Pattern.quote(value) + "(?!\\w)");
        return standalone.matcher(text).replaceAll(Matcher.quoteReplacement(INPUT));
    }

    /**
     * Quoted code identifiers are kept even when they equal a sent field name: in {@code (reading 'email')} the
     * identifier names the crash site, see {@link QuotedFragments}.
     */
    private static String maskFieldName(String token, Set<String> fieldNames) {
        Matcher matcher = TOKEN_CORE.matcher(token);
        if (fieldNames.isEmpty() || QuotedFragments.isPinned(token) || !matcher.matches() || !isFieldName(matcher.group(2), fieldNames)) {
            return token;
        }
        return matcher.group(1) + FIELD + matcher.group(3);
    }

    private static boolean isFieldName(String core, Set<String> fieldNames) {
        return core.length() >= MIN_FIELD_NAME_LENGTH && !MESSAGE_WORDS.contains(core.toLowerCase(Locale.ROOT))
                && (fieldNames.contains(core) || fieldNames.contains(StringUtils.substringAfterLast(core, ".")));
    }
}
