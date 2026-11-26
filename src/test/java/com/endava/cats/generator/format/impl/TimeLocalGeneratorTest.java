package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

@QuarkusTest
class TimeLocalGeneratorTest {
    private TimeLocalGenerator generator;

    @BeforeEach
    void setup() {
        generator = new TimeLocalGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "time-local,,true", "local-time,,true", "timeLocal,,true",
            ",localTime,true", ",schedule#time_local,true",
            "time,,false", "date-time-local,,false", ",arrivalTime,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidLocalTime() {
        String value = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(value).matches("^\\d{2}:\\d{2}:\\d{2}$");
        Assertions.assertThatNoException().isThrownBy(() -> LocalTime.parse(value));
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("25:61:00");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("99:99:99");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("time-local", "local-time");
    }
}
