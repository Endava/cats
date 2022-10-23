package com.endava.cats.model.generator.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class PasswordGeneratorTest {

    @Test
    void shouldGenerate() {
        PasswordGenerator passwordGenerator = new PasswordGenerator();
        Assertions.assertThat(passwordGenerator.generate(new Schema<>())).isEqualTo("catsISc00l?!useIt#");
    }

    @ParameterizedTest
    @CsvSource({"password,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        PasswordGenerator passwordGenerator = new PasswordGenerator();
        Assertions.assertThat(passwordGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
