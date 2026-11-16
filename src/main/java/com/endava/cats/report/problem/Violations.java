package com.endava.cats.report.problem;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the violation a response check reported in the result details, masked so that values which differ per test
 * (numbers, lengths, ids) do not split one violation.
 */
final class Violations {
    private static final String VALIDATION_ERRORS = "Validation errors:";
    private static final Pattern ACTUAL_VALUE = Pattern.compile("actual \\[([^\\]]*)]");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final int MAX_LENGTH = 160;

    private Violations() {
    }

    /**
     * Returns the masked violation of a response check: the actual value it reported (e.g. the content type), or else
     * the masked details.
     *
     * @param resultDetails the result details of a test
     * @return the masked violation
     */
    static String of(String resultDetails) {
        String details = StringUtils.defaultString(resultDetails);
        return actualValue(details).orElseGet(() -> masked(details));
    }

    /**
     * Returns the masked validator messages of a schema mismatch.
     *
     * @param resultDetails the result details of a test
     * @return the masked validator messages, or the masked details when there are none
     */
    static String validatorMessages(String resultDetails) {
        String details = StringUtils.defaultString(resultDetails);
        return masked(details.contains(VALIDATION_ERRORS) ? StringUtils.substringAfterLast(details, VALIDATION_ERRORS) : details);
    }

    private static Optional<String> actualValue(String details) {
        Matcher matcher = ACTUAL_VALUE.matcher(details);
        return matcher.find() ? Optional.of(StringUtils.substringBefore(matcher.group(1), ";").trim()) : Optional.empty();
    }

    private static String masked(String text) {
        return StringUtils.abbreviate(DIGITS.matcher(text.trim()).replaceAll("N"), MAX_LENGTH);
    }
}
