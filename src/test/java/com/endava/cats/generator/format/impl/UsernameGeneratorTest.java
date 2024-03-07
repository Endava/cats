package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class UsernameGeneratorTest {
    @Test
    void shouldGenerate() {
        UsernameGenerator usernameGenerator = new UsernameGenerator();
        Assertions.assertThat(usernameGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"username,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        UsernameGenerator usernameGenerator = new UsernameGenerator();
        Assertions.assertThat(usernameGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"username,true", "USERNAME,true", "other#username,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        UsernameGenerator usernameGenerator = new UsernameGenerator();
        Assertions.assertThat(usernameGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
