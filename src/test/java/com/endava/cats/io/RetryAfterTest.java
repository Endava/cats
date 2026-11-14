package com.endava.cats.io;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class RetryAfterTest {

    private static final long FIXED_NOW_MILLIS = Instant.parse("2026-11-14T12:00:00Z").toEpochMilli();

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1000",
            "5, 5000",
            "30, 30000",
            "' 12 ', 12000",
            "'+3', 3000",
            "'Sat, 14 Nov 2026 12:00:15 GMT', 15000",
            "'Sat, 14 Nov 2026 12:00:00 GMT', 0",
            "'Saturday, 14-Nov-26 12:00:10 GMT', 10000",
            "'Sat Nov 14 12:00:20 2026', 20000"
    })
    void shouldParseValidDelaySecondsAndHttpDates(String headerValue, long expectedMillis) {
        OptionalLong parsed = RetryAfter.parseMillis(headerValue, FIXED_NOW_MILLIS);

        assertThat(parsed).hasValue(expectedMillis);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "-1",
            "-60",
            "+",
            "-",
            "1.5",
            "abc",
            "not-a-date-or-number",
            "Sat, 14 Nov 2026 11:59:59 GMT",
            "Sun, 06 Nov 1994 08:49:37 GMT",
            "99999999999999999999999999999"
    })
    void shouldReturnEmptyForMissingNegativePastOrMalformedValues(String invalidHeader) {
        OptionalLong parsed = RetryAfter.parseMillis(invalidHeader, FIXED_NOW_MILLIS);

        assertThat(parsed).isEmpty();
    }

    @Test
    void shouldCapHugeDelaySecondsAtLongMaxValueAndSupportSystemClockOverload() {
        OptionalLong huge = RetryAfter.parseMillis(String.valueOf(Long.MAX_VALUE), FIXED_NOW_MILLIS);
        OptionalLong systemClock = RetryAfter.parseMillis("2");

        assertThat(huge).hasValue(Long.MAX_VALUE);
        assertThat(systemClock).hasValue(2_000L);
    }
}
