package com.endava.cats.command.triage;

import com.endava.cats.report.ErrorSimilarityDetector;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The root-cause error text of a response: a short display {@code signature}, the {@code clean} message with leading
 * error codes / reason phrases stripped (e.g. {@code "XEL-4220: Bad Request: name: must not be blank"} becomes
 * {@code "name: must not be blank"}), and whether the body is an HTML error page.
 */
record ErrorMessage(String signature, String clean, boolean html) {
    private static final int SIGNATURE_LENGTH = 90;
    private static final int MAX_QUALIFIERS = 3;
    private static final Pattern LEADING_SEGMENT = Pattern.compile("^([^:]{2,40}):\\s+(.+)$", Pattern.DOTALL);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.\\[\\]]*");
    private static final Pattern CODE_LIKE = Pattern.compile("[A-Z]+[-_]?\\d+[A-Z0-9_-]*|\\d+|[A-Z][A-Z0-9_-]+");
    private static final Set<String> GENERIC_HEADS = Set.of("error", "errors", "message", "validation", "exception",
            "warning", "detail", "details", "reason", "fault", "problem", "violation", "constraintviolation");

    static ErrorMessage of(TriageTestCase testCase) {
        String rawBody = testCase.rawResponseBody();
        String fallback = StringUtils.defaultIfBlank(testCase.testCase().getResultReason(), "HTTP " + testCase.statusCode());
        String summary = ErrorSimilarityDetector.extractRootCauseSummary(rawBody, fallback);
        return new ErrorMessage(StringUtils.abbreviate(summary, "...", SIGNATURE_LENGTH + 3),
                stripQualifiers(summary.trim()), looksLikeHtml(rawBody));
    }

    String lower() {
        return clean.toLowerCase(Locale.ROOT);
    }

    static String stripQualifiers(String message) {
        String current = message;
        for (int i = 0; i < MAX_QUALIFIERS; i++) {
            Matcher matcher = LEADING_SEGMENT.matcher(current);
            if (!matcher.matches() || !isQualifier(matcher.group(1).trim())) {
                return current;
            }
            current = matcher.group(2).trim();
        }
        return current;
    }

    private static boolean isQualifier(String head) {
        if (!IDENTIFIER.matcher(head).matches()) {
            return true;
        }
        return CODE_LIKE.matcher(head).matches() || GENERIC_HEADS.contains(head.toLowerCase(Locale.ROOT));
    }

    private static boolean looksLikeHtml(String rawBody) {
        String lower = StringUtils.trimToEmpty(rawBody).toLowerCase(Locale.ROOT);
        return lower.startsWith("<!doctype") || lower.startsWith("<html") || lower.contains("<body");
    }
}
