package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

@QuarkusTest
class DateTimeLocalGeneratorTest {
    private DateTimeLocalGenerator generator;

    @BeforeEach
    void setup() {
        generator = new DateTimeLocalGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "date-time-local,,true", "local-date-time,,true", "dateTimeLocal,,true",
            ",localDateTime,true", ",event#date_time_local,true",
            "date-time,,false", "time-local,,false", ",arrivalTime,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidLocalDateTime() {
        String value = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(value).doesNotEndWith("Z");
        Assertions.assertThatNoException().isThrownBy(() -> LocalDateTime.parse(value));
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("2021-07-21T10:22:01Z");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("1111-07-21T88:32:28");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("date-time-local", "local-date-time");
    }
}
