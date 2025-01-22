package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class AirportCodeGeneratorTest {
    @Test
    void shouldGenerate() {
        AirportCodeGenerator airportCodeGenerator = new AirportCodeGenerator();
        Assertions.assertThat(airportCodeGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"airportCode,true", "other,false", "airport,true"})
    void shouldApplyToFormat(String format, boolean expected) {
        AirportCodeGenerator airportCodeGenerator = new AirportCodeGenerator();
        Assertions.assertThat(airportCodeGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"airportCode,true", "AIRPORTCODE,true", "airportCode,true", "airport_code,true", "other#airportCode,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        AirportCodeGenerator airportCodeGenerator = new AirportCodeGenerator();
        Assertions.assertThat(airportCodeGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
