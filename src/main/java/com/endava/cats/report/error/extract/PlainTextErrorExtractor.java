package com.endava.cats.report.error.extract;

import java.util.Optional;

/**
 * The fallback extractor: the whole text is the message, unless it holds a stack trace, in which case the message is
 * the text that goes with the trace (e.g. {@code panic: ...} before a Go trace, {@code KeyError: 'name'} after a
 * Python traceback) and the trace gives the crash-site frame.
 */
public final class PlainTextErrorExtractor implements ErrorBodyExtractor {

    @Override
    public Optional<ErrorCause> extract(String body) {
        String text = body.strip();
        return Optional.of(StackFrameParser.parse(text)
                .map(trace -> ErrorCause.of("", trace.message(text), trace.frame()))
                .orElseGet(() -> ErrorCause.of("", text, "")));
    }
}
