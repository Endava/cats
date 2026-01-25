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
class DateOfBirthGeneratorTest {

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
    }
    @Test
    void shouldGenerate() {
        DateOfBirthGenerator dateOfBirthGenerator = new DateOfBirthGenerator();
        Assertions.assertThat(dateOfBirthGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"dob,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        DateOfBirthGenerator dateOfBirthGenerator = new DateOfBirthGenerator();
        Assertions.assertThat(dateOfBirthGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"dateOfBirth,true", "dob,true", "birthday,true", "date_of_birth,true", "other#dob,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        DateOfBirthGenerator dateOfBirthGenerator = new DateOfBirthGenerator();
        Assertions.assertThat(dateOfBirthGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
