package com.endava.cats.report.error.mask;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Recognizes error codes such as {@code USR-001}, {@code err-01}, {@code E_42}, {@code auth.token.expired} or
 * {@code ERR:12}. They identify the error, so error templates keep them verbatim instead of masking their digits.
 * <p>
 * A code is a single token of letters and digits, optionally joined by {@code - _ . :}. Per-occurrence values that
 * look alike are not codes, otherwise every order id would become its own template: UUIDs, hashes (32 or more hex
 * digits), timestamps and anything holding a run of {@value #MAX_DIGIT_RUN} or more digits ({@code ORD-83749213}).
 */
public final class ErrorTypeCodes {
    private static final int MAX_LENGTH = 64;
    private static final int MAX_DIGIT_RUN = 6;
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9]+(?:[-_.:][A-Za-z0-9]+)*");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final List<Pattern> PER_OCCURRENCE = List.of(
            Pattern.compile("\\d{%d,}".formatted(MAX_DIGIT_RUN)),
            Pattern.compile("[0-9a-fA-F]{32,}"),
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}"));

    private ErrorTypeCodes() {
    }

    /**
     * Tells whether a value is an error code that identifies the error.
     *
     * @param value an error type read from a code or type field
     * @return {@code true} if the value must be kept as it is
     */
    static boolean isCode(String value) {
        return value.length() <= MAX_LENGTH && CODE.matcher(value).matches() && LETTER.matcher(value).find()
                && PER_OCCURRENCE.stream().noneMatch(pattern -> pattern.matcher(value).find());
    }
}
