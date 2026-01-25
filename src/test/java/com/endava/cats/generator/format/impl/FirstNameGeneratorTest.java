package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class FirstNameGeneratorTest {

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
    }

    @Test
    void shouldGenerate() {
        FirstNameGenerator firstNameGenerator = new FirstNameGenerator();
        Assertions.assertThat(firstNameGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"firstname,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        FirstNameGenerator firstNameGenerator = new FirstNameGenerator();
        Assertions.assertThat(firstNameGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"firstname,true", "FIRSTNAME,true", "firstName,true", "first_name,true", "other#firstName,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        FirstNameGenerator firstNameGenerator = new FirstNameGenerator();
        Assertions.assertThat(firstNameGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
