package com.endava.cats.report.error.extract;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts the root cause of HTML error pages (Tomcat, Spring Whitelabel, Werkzeug, Express, Django, nginx...): the
 * title is the error type and the visible text is the message, so the page's CSS and markup never reach the
 * template. Stack traces in {@code <pre>} blocks (Express development pages, Tomcat exception reports, the Werkzeug
 * debugger) give the crash-site frame and are left out of the message.
 */
public final class HtmlErrorExtractor implements ErrorBodyExtractor {
    private static final Pattern HTML_START = Pattern.compile("(?is)\\s*<(?:!doctype\\s+html|html|head|body|title|h1|pre)\\b.*");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern INVISIBLE = Pattern.compile("(?is)<!--.*?(?:-->|$)|<(style|script|head)\\b.*?(?:</\\1>|$)");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>|<hr\\b[^>]*>|</(?:p|div|h[1-6]|li|tr|pre|title|table|ul|ol)>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>?");
    private static final Pattern SPACES = Pattern.compile("[ \\u00a0]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public Optional<ErrorCause> extract(String body) {
        if (!HTML_START.matcher(body).matches()) {
            return Optional.empty();
        }
        String title = title(body);
        String text = visibleText(body);
        return Optional.of(StackFrameParser.parse(text)
                .map(trace -> cause(title, trace.message(text), trace.frame()))
                .orElseGet(() -> cause(title, text, "")));
    }

    private static ErrorCause cause(String title, String text, String frame) {
        String errorType = title.isEmpty() ? text.lines().findFirst().orElse("").strip() : title;
        String message = text.lines().map(WHITESPACE::matcher).map(matcher -> matcher.replaceAll(" ").strip())
                .filter(StringUtils::isNotEmpty).filter(line -> !line.equalsIgnoreCase(errorType))
                .collect(Collectors.joining(" "));
        return ErrorCause.of(errorType, message, frame);
    }

    private static String title(String body) {
        Matcher matcher = TITLE.matcher(body);
        return matcher.find() ? WHITESPACE.matcher(unescape(matcher.group(1))).replaceAll(" ").strip() : "";
    }

    /**
     * The text a browser would show, one line per block element, with the indentation of {@code <pre>} lines kept
     * so stack frames can still be recognized.
     */
    private static String visibleText(String body) {
        String withoutInvisible = INVISIBLE.matcher(TITLE.matcher(body).replaceAll("")).replaceAll("");
        String withLineBreaks = LINE_BREAK.matcher(withoutInvisible).replaceAll("\n");
        return SPACES.matcher(unescape(TAG.matcher(withLineBreaks).replaceAll(""))).replaceAll(" ");
    }

    private static String unescape(String html) {
        return StringEscapeUtils.unescapeHtml4(html);
    }
}
