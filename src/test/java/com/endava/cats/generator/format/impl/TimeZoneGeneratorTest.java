package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class TimeZoneGeneratorTest {
    @Test
    void shouldGenerate() {
        TimeZoneGenerator timeZoneGenerator = new TimeZoneGenerator();
        Assertions.assertThat(timeZoneGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"timeZone,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        TimeZoneGenerator timeZoneGenerator = new TimeZoneGenerator();
        Assertions.assertThat(timeZoneGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"timeZone,true", "TIMEZONE,true", "timeZone,true", "time_zone,true", "other#timeZone,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        TimeZoneGenerator timeZoneGenerator = new TimeZoneGenerator();
        Assertions.assertThat(timeZoneGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
