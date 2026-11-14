package com.endava.cats.report.error.extract;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * The root cause extracted from an error response body, before any masking.
 *
 * @param errorType         the error type or title, e.g. {@code Bad Request} or {@code NullPointerException}, or empty
 * @param messages          the error messages; validation errors often carry several, e.g. one per invalid field
 * @param stackFrame        the first application stack frame as {@code function@file:line}, or empty
 * @param typeFromCodeField {@code true} when the error type was read from a code or type field and not from free text
 *                          such as a title, so that a code-like value may be kept verbatim
 */
public record ErrorCause(String errorType, List<String> messages, String stackFrame, boolean typeFromCodeField) {
    static final ErrorCause EMPTY = new ErrorCause("", List.of(), "");

    public ErrorCause {
        messages = messages.stream().map(String::strip).filter(StringUtils::isNotEmpty).toList();
    }

    public ErrorCause(String errorType, List<String> messages, String stackFrame) {
        this(errorType, messages, stackFrame, false);
    }

    static ErrorCause of(String errorType, String message, String stackFrame) {
        return new ErrorCause(errorType, List.of(message), stackFrame);
    }

    boolean isEmpty() {
        return errorType.isEmpty() && messages.isEmpty();
    }

    /**
     * Returns the messages in their original order, without duplicates, joined with {@code "; "}.
     *
     * @param maxMessages the maximum number of messages to keep
     * @return the joined messages
     */
    String detail(int maxMessages) {
        return String.join("; ", messages.stream().distinct().limit(maxMessages).toList());
    }
}
