package com.endava.cats.report.error.extract;

import org.apache.commons.text.StringEscapeUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Recovers the string fields that survive in a JSON body cut in the middle, which is what report summaries hold: they
 * keep only the first 1000 characters of a response body.
 */
public final class TruncatedJsonFields {
    private static final Pattern STRING_FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern TRACE_FIELD = Pattern.compile(
            "\"(?:trace|stackTrace|stack|exceptionTrace|stack_trace|backtrace)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)");

    private TruncatedJsonFields() {
    }

    /**
     * Collects the string fields at any depth, the first occurrence of each name winning.
     *
     * @param truncated the body, cut at any point
     * @return the unescaped values by field name, in document order
     */
    static Map<String, String> recover(String truncated) {
        return STRING_FIELD.matcher(truncated).results().collect(Collectors.toMap(match -> match.group(1),
                match -> unescape(match.group(2)), (first, _) -> first, LinkedHashMap::new));
    }

    /**
     * The trace field is usually the one the cut goes through, so its value may have no closing quote.
     *
     * @param truncated the body, cut at any point
     * @return the unescaped trace, or an empty string
     */
    static String trace(String truncated) {
        Matcher trace = TRACE_FIELD.matcher(truncated);
        return trace.find() ? unescape(trace.group(1)) : "";
    }

    static String unescape(String jsonString) {
        try {
            return StringEscapeUtils.unescapeJson(jsonString);
        } catch (IllegalArgumentException _) {
            return jsonString;
        }
    }
}
