package com.endava.cats.io;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Parses HTTP {@code Retry-After} header values according to RFC 9110 §10.2.3 (delay-seconds or HTTP-date).
 */
public final class RetryAfter {

    private static final long MAX_DELAY_SECONDS = Long.MAX_VALUE / 1_000L;

    private static final DateTimeFormatter RFC_850_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEEE, dd-MMM-")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 1970)
            .appendPattern(" HH:mm:ss zzz")
            .toFormatter(Locale.US)
            .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter ASCTIME_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE MMM ")
            .padNext(2)
            .appendValue(ChronoField.DAY_OF_MONTH)
            .appendPattern(" HH:mm:ss yyyy")
            .toFormatter(Locale.US)
            .withZone(ZoneOffset.UTC);

    private static final List<DateTimeFormatter> HTTP_DATE_FORMATTERS = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            RFC_850_DATE_TIME,
            ASCTIME_DATE_TIME);

    private RetryAfter() {
        // Utility class
    }

    /**
     * Parses a {@code Retry-After} header value into a non-negative delay in milliseconds using the current system clock.
     *
     * @param headerValue the raw {@code Retry-After} header value
     * @return the non-negative delay in milliseconds, or empty if missing, negative, in the past, or malformed
     */
    public static OptionalLong parseMillis(String headerValue) {
        return parseMillis(headerValue, System.currentTimeMillis());
    }

    /**
     * Parses a {@code Retry-After} header value into a non-negative delay in milliseconds relative to {@code nowEpochMillis}.
     *
     * @param headerValue    the raw {@code Retry-After} header value
     * @param nowEpochMillis current epoch time in milliseconds
     * @return the non-negative delay in milliseconds, or empty if missing, negative, in the past, or malformed
     */
    public static OptionalLong parseMillis(String headerValue, long nowEpochMillis) {
        if (headerValue == null || headerValue.isBlank()) {
            return OptionalLong.empty();
        }
        String trimmed = headerValue.trim();
        if (isSignedOrUnsignedInteger(trimmed)) {
            return parseDelaySeconds(trimmed);
        }
        return parseHttpDate(trimmed, nowEpochMillis);
    }

    private static boolean isSignedOrUnsignedInteger(String value) {
        int start = (value.charAt(0) == '+' || value.charAt(0) == '-') ? 1 : 0;
        if (start == value.length()) {
            return false;
        }
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static OptionalLong parseDelaySeconds(String trimmed) {
        if (trimmed.charAt(0) == '-') {
            return OptionalLong.empty();
        }
        try {
            long seconds = Long.parseLong(trimmed);
            long millis = seconds > MAX_DELAY_SECONDS ? Long.MAX_VALUE : seconds * 1_000L;
            return OptionalLong.of(millis);
        } catch (NumberFormatException _) {
            return OptionalLong.empty();
        }
    }

    private static OptionalLong parseHttpDate(String trimmed, long nowEpochMillis) {
        for (DateTimeFormatter formatter : HTTP_DATE_FORMATTERS) {
            try {
                Instant instant = formatter.parse(trimmed, Instant::from);
                long deltaMillis = instant.toEpochMilli() - nowEpochMillis;
                return deltaMillis < 0 ? OptionalLong.empty() : OptionalLong.of(deltaMillis);
            } catch (DateTimeParseException _) {
                // try next RFC 9110 HTTP-date format
            }
        }
        return OptionalLong.empty();
    }
}
