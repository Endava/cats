package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class FlightCodeGeneratorTest {
    @Test
    void shouldGenerate() {
        FlightCodeGenerator flightCodeGenerator = new FlightCodeGenerator();
        Assertions.assertThat(flightCodeGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"flightCode,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        FlightCodeGenerator flightCodeGenerator = new FlightCodeGenerator();
        Assertions.assertThat(flightCodeGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"flightCode,true", "FLIGHTCODE,true", "flightCode,true", "flight_code,true", "other#flightCode,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        FlightCodeGenerator flightCodeGenerator = new FlightCodeGenerator();
        Assertions.assertThat(flightCodeGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
