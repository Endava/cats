package com.endava.cats.report.error.extract;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Finds stack traces of the JVM, Node.js, .NET, Python, Go, Ruby and PHP in error text and picks the frame that
 * identifies the crash site, normalized to {@code function@file:line}.
 * <p>
 * Frames of the language runtime are never picked. Library and framework frames are picked only when the trace holds
 * no application frame, which happens when the body is cut before the application frames. The syntaxes are tried in
 * table order and the first one that matches any frame wins; their patterns do not overlap.
 */
public final class StackFrameParser {
    private static final Pattern NEVER = Pattern.compile("[^\\s\\S]");
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");
    private static final Pattern TRACEBACK_HEADER = Pattern.compile("(?i)traceback \\(most recent call last\\):?");
    private static final Pattern INDENTED_FRAME_LINE = Pattern.compile("(?m)^[ \\t]+at\\s");

    private StackFrameParser() {
    }

    /**
     * Finds the stack trace in the given text.
     *
     * @param text the error text
     * @return the trace, or empty when the text holds no recognizable stack frame
     */
    static Optional<StackTrace> parse(String text) {
        if (StringUtils.isBlank(text)) {
            return Optional.empty();
        }
        return Arrays.stream(FrameSyntax.values()).map(syntax -> syntax.trace(text)).flatMap(Optional::stream).findFirst();
    }

    /**
     * Returns the crash-site frame of the given text.
     *
     * @param text the error text
     * @return the frame as {@code function@file:line}, or empty
     */
    static String crashSite(String text) {
        return parse(text).map(StackTrace::frame).orElse("");
    }

    /**
     * A stack trace found in a text.
     *
     * @param start the index of the first frame
     * @param end   the index after the last frame
     * @param frame the crash-site frame as {@code function@file:line}, or empty when all frames belong to the runtime
     */
    record StackTrace(int start, int end, String frame) {

        /**
         * Returns the error message that goes with the trace: the first paragraph before it, or, for Python
         * tracebacks that put the exception after the frames, the first line after it that is not indented.
         *
         * @param text the text the trace was found in
         * @return the message, or empty
         */
        String message(String text) {
            String before = PARAGRAPH_BREAK.splitAsStream(INDENTED_FRAME_LINE.split(text.substring(0, start), 2)[0]).map(String::strip)
                    .filter(StringUtils::isNotEmpty).findFirst().orElse("");
            if (!before.isEmpty() && !TRACEBACK_HEADER.matcher(before).matches()) {
                return before;
            }
            return text.substring(end).lines().filter(StringUtils::isNotBlank)
                    .filter(line -> !Character.isWhitespace(line.charAt(0))).findFirst().map(String::strip).orElse(before);
        }
    }

    private enum Origin {RUNTIME, LIBRARY, APPLICATION}

    private record Frame(String function, String file, String line, Origin origin) {

        String render() {
            String location = file.isEmpty() ? "" : file + (line.isEmpty() ? "" : ":" + line);
            if (function.isEmpty() || location.isEmpty()) {
                return function + location;
            }
            return function + "@" + location;
        }
    }

    private enum FrameSyntax {
        JVM("(?m)(?:\\bat\\s+|^[ \\t]+)(?:[\\w.$@-]+/{1,2})?(?<fn>[A-Za-z_$][\\w$]*(?:\\.[\\w$<>-]+)+)"
                + "\\((?:(?<file>[\\w$.-]+\\.(?:java|kt|kts|scala|groovy|clj)):(?<line>\\d+)|Native Method|Unknown Source|<generated>)\\)",
                "^(?:java|jdk|sun|com\\.sun)\\.",
                "^(?:org\\.springframework|org\\.apache|org\\.hibernate|org\\.glassfish|org\\.jboss|org\\.eclipse|org\\.h2"
                        + "|org\\.postgresql|org\\.mariadb|org\\.slf4j|com\\.fasterxml|com\\.google|com\\.zaxxer|com\\.mysql"
                        + "|io\\.netty|io\\.quarkus|io\\.vertx|io\\.smallrye|io\\.undertow|io\\.micronaut|jakarta|javax"
                        + "|reactor|kotlin|kotlinx|scala|akka|ch\\.qos|net\\.bytebuddy)\\.|\\$\\$|\\$Proxy",
                "(?m)^\\s*(?:Caused by:|root cause)", false),
        DOTNET("\\bat\\s+(?<fn>[\\w.`<>$+\\[\\],|]+)\\((?!Native Method\\)|Unknown Source\\))(?:[^():\\n]*\\s[^():\\n]*)?\\)"
                + "(?:\\s+in\\s+(?<file>[^\\n]+?):line\\s+(?<line>\\d+))?",
                "^System\\.", "^(?:Microsoft\\.|lambda_method)", null, false),
        NODE("\\bat\\s+(?:async\\s+)?(?:new\\s+)?(?:(?<fn>[^\\s()]+)(?:\\s\\[as\\s[^\\]]+])?\\s\\()?"
                + "(?<file>(?:file://)?(?=[^\\s()]*(?:[/\\\\]|\\.[A-Za-z]))[^\\s()]+?):(?<line>\\d+):\\d+\\)?",
                "@(?:node:|internal/|<anonymous>|native\\b)", "node_modules", null, false),
        PYTHON("File \"(?<file>[^\"\\n]+)\",\\s+line (?<line>\\d+),\\s+in (?<fn>\\S+)",
                "@<|/lib/python\\d[^/]*/(?!(?:site|dist)-packages/)", "(?:site|dist)-packages/|\\.pyx$", null, true),
        GO("(?m)^(?<fn>\\S+)\\([^()\\n]*\\)\\n\\s+(?<file>\\S+\\.go):(?<line>\\d+)",
                "^(?:runtime[./]|panic@|internal/)|/go/src/",
                "/pkg/mod/|^(?:github\\.com|golang\\.org|gopkg\\.in|google\\.golang\\.org)/", "(?m)^panic\\(", false),
        RUBY("(?<file>(?:[\\w-]+ \\([\\w.]+\\) )?[^\\s\"'\\[\\]:]+\\.rb):(?<line>\\d+):in [`'](?<fn>[^'`\\n]+)'",
                "@<internal:|/lib/ruby/\\d[^/]*/(?!gems/)", "\\) |/gems/", null, false),
        PHP("#\\d+\\s+(?<file>[^\\s(]+\\.php)\\((?<line>\\d+)\\):\\s+(?<fn>[^\\s(]+)\\(", null, "/vendor/", null, false);

        private final Pattern frame;
        private final Pattern runtime;
        private final Pattern library;
        private final Pattern crashSectionStart;
        private final boolean innermostLast;

        FrameSyntax(String frame, String runtime, String library, String crashSectionStart, boolean innermostLast) {
            this.frame = Pattern.compile(frame);
            this.runtime = runtime == null ? NEVER : Pattern.compile(runtime);
            this.library = Pattern.compile(library);
            this.crashSectionStart = crashSectionStart == null ? NEVER : Pattern.compile(crashSectionStart);
            this.innermostLast = innermostLast;
        }

        Optional<StackTrace> trace(String text) {
            List<MatchResult> matches = frame.matcher(text).results().toList();
            if (matches.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new StackTrace(matches.getFirst().start(), matches.getLast().end(),
                    crashSite(crashSection(text, matches)).map(Frame::render).orElse("")));
        }

        /**
         * The frames after the last section start (Java {@code Caused by:}, Go {@code panic(}) when there is one:
         * frames before it belong to the wrapping exception or to the recovery handler.
         */
        private List<Frame> crashSection(String text, List<MatchResult> matches) {
            int sectionStart = crashSectionStart.matcher(text).results().map(MatchResult::start).reduce((_, last) -> last).orElse(0);
            List<MatchResult> section = matches.stream().filter(match -> match.start() >= sectionStart).toList();
            List<Frame> frames = (section.isEmpty() ? matches : section).stream().map(this::toFrame).toList();
            return innermostLast ? frames.reversed() : frames;
        }

        private static Optional<Frame> crashSite(List<Frame> innermostFirst) {
            return innermostFirst.stream().filter(frame -> frame.origin() == Origin.APPLICATION).findFirst()
                    .or(() -> innermostFirst.stream().filter(frame -> frame.origin() == Origin.LIBRARY).findFirst());
        }

        private Frame toFrame(MatchResult match) {
            String function = StringUtils.defaultString(match.group("fn"));
            String file = StringUtils.defaultString(match.group("file"));
            return new Frame(function, file, StringUtils.defaultString(match.group("line")), origin(function + "@" + file));
        }

        private Origin origin(String location) {
            if (runtime.matcher(location).find()) {
                return Origin.RUNTIME;
            }
            return library.matcher(location).find() ? Origin.LIBRARY : Origin.APPLICATION;
        }
    }
}
