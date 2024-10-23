package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class GenderGeneratorTest {

    @Test
    void shouldGenerate() {
        GenderGenerator genderGenerator = new GenderGenerator();
        Assertions.assertThat(genderGenerator.generate(new Schema<>()).toString()).containsAnyOf("Male", "Female", "Other");
    }

    @ParameterizedTest
    @CsvSource({"gender,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        GenderGenerator genderGenerator = new GenderGenerator();
        Assertions.assertThat(genderGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"gender,true", "GENDER,true", "other,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        GenderGenerator genderGenerator = new GenderGenerator();
        Assertions.assertThat(genderGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
