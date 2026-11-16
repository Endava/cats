package com.endava.cats.report.error.extract;

import com.endava.cats.report.error.contract.ErrorResponseContract;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracts the root cause of an error response body, whatever the stack that produced it: HTML error pages first,
 * then JSON error objects, then plain text. Used for error templates, WFC fault contexts, HTML report cluster
 * summaries and triage.
 */
public final class ErrorBodyExtraction {
    static final int MAX_SUMMARY_MESSAGES = 5;
    private static final int MAX_ANALYZED_LENGTH = 32_768;
    private static final Pattern JSON_STRING = Pattern.compile("(?s)\"((?:[^\"\\\\]|\\\\.)*+)(?:\"|\\\\?)");
    private static final int SUMMARY_LENGTH = 160;

    private ErrorBodyExtraction() {
    }

    /**
     * Extracts the root cause of an error response body, reading JSON fields by their names.
     *
     * @param responseBody the response body
     * @return the root cause, empty for blank bodies
     * @see #extract(String, ErrorResponseContract)
     */
    public static ErrorCause extract(String responseBody) {
        return extract(responseBody, ErrorResponseContract.NONE);
    }

    /**
     * Extracts the root cause of an error response body. Bodies longer than 32 KiB are analyzed by their head and
     * tail, since crash details sit at the start (most runtimes) or at the end (Python tracebacks).
     *
     * @param responseBody the response body
     * @param contract     what the OpenAPI contract declares about the error body; it only applies to a JSON body
     *                     that matches it
     * @return the root cause, empty for blank bodies
     */
    public static ErrorCause extract(String responseBody, ErrorResponseContract contract) {
        if (EmptyBody.isEmpty(responseBody)) {
            return ErrorCause.EMPTY;
        }
        String analyzed = headAndTail(unwrapJsonString(responseBody));
        return extractors(contract).stream().map(extractor -> extractor.extract(analyzed)).flatMap(Optional::stream)
                .findFirst().orElse(ErrorCause.EMPTY);
    }

    private static List<ErrorBodyExtractor> extractors(ErrorResponseContract contract) {
        return List.of(new HtmlErrorExtractor(), new StructuredJsonErrorExtractor(contract), new PlainTextErrorExtractor());
    }

    /**
     * Some servers send the error message as a JSON string ({@code "Cannot read properties of undefined"}); its
     * content is the body to analyze, otherwise the quote policy would mask the whole message. Report summaries may
     * cut such a string before its closing quote.
     */
    private static String unwrapJsonString(String body) {
        Matcher jsonString = JSON_STRING.matcher(body.strip());
        if (!jsonString.matches()) {
            return body;
        }
        try {
            return StringEscapeUtils.unescapeJson(jsonString.group(1));
        } catch (IllegalArgumentException _) {
            return body;
        }
    }

    private static String headAndTail(String body) {
        if (body.length() <= MAX_ANALYZED_LENGTH) {
            return body;
        }
        int half = MAX_ANALYZED_LENGTH / 2;
        return body.substring(0, half) + "\n" + body.substring(body.length() - half);
    }

    /**
     * Builds a concise, human-readable one-line root-cause summary, e.g.
     * {@code TypeError: Cannot read properties of undefined (reading 'id') @ ownerId@/app/app.js:26}.
     *
     * @param responseBody    the response body
     * @param fallbackDetails the text to use when the body is empty
     * @return the summary, at most 160 characters long
     */
    public static String rootCauseSummary(String responseBody, String fallbackDetails) {
        if (EmptyBody.isEmpty(responseBody)) {
            return StringUtils.isNotBlank(fallbackDetails) ? summaryLine(fallbackDetails) : EmptyBody.PLACEHOLDER;
        }
        ErrorCause cause = extract(responseBody);
        String detail = cause.detail(MAX_SUMMARY_MESSAGES);
        String message = Stream.of(cause.errorType(), detail.equalsIgnoreCase(cause.errorType()) ? "" : detail)
                .filter(StringUtils::isNotEmpty).collect(Collectors.joining(": "));
        String summary = cause.stackFrame().isEmpty() ? message : message + " @ " + cause.stackFrame();
        return summaryLine(StringUtils.defaultIfBlank(summary, responseBody));
    }

    private static String summaryLine(String text) {
        return StringUtils.abbreviate(StringUtils.normalizeSpace(text), "...", SUMMARY_LENGTH);
    }
}
