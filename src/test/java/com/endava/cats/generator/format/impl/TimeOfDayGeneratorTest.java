package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class TimeOfDayGeneratorTest {
    @Test
    void shouldGenerate() {
        TimeOfDayGenerator timeOfDayGenerator = new TimeOfDayGenerator();
        Assertions.assertThat(timeOfDayGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"arrivalTime,false", "other,false", "time,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        TimeOfDayGenerator timeOfDayGenerator = new TimeOfDayGenerator();
        Assertions.assertThat(timeOfDayGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"arrivalTime,true", "ARRIVALTIME,true", "other#departureTime,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        TimeOfDayGenerator timeOfDayGenerator = new TimeOfDayGenerator();
        Assertions.assertThat(timeOfDayGenerator.appliesTo("", property)).isEqualTo(expected);
    }

    @Test
    void shouldNotApplyToTimeFormat() {
        TimeOfDayGenerator timeOfDayGenerator = new TimeOfDayGenerator();
        Assertions.assertThat(timeOfDayGenerator.appliesTo("time", "arrivalTime")).isFalse();
    }
}
