package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class LastNameGeneratorTest {
    @Test
    void shouldGenerate() {
        LastNameGenerator lastNameGenerator = new LastNameGenerator();
        Assertions.assertThat(lastNameGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"lastname,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        LastNameGenerator lastNameGenerator = new LastNameGenerator();
        Assertions.assertThat(lastNameGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"lastname,true", "LASTNAME,true", "lastName,true", "last_name,true", "other#lastName,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        LastNameGenerator lastNameGenerator = new LastNameGenerator();
        Assertions.assertThat(lastNameGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
