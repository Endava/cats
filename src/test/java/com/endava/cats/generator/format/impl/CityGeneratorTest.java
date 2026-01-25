package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CityGeneratorTest {

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
    }

    @Test
    void shouldGenerate() {
        CityGenerator cityGenerator = new CityGenerator();
        Assertions.assertThat(cityGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(5);
    }

    @ParameterizedTest
    @CsvSource({"city,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        CityGenerator cityGenerator = new CityGenerator();
        Assertions.assertThat(cityGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"city,true", "CITY,true", "cityName,true", "city_name,true", "other#city,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        CityGenerator cityGenerator = new CityGenerator();
        Assertions.assertThat(cityGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
