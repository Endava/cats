package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class LanguageGeneratorTest {
    @Test
    void shouldGenerate() {
        LanguageGenerator languageGenerator = new LanguageGenerator();
        Assertions.assertThat(languageGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"language,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        LanguageGenerator languageGenerator = new LanguageGenerator();
        Assertions.assertThat(languageGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"language,true", "LANGUAGE,true", "language,true", "other#language,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        LanguageGenerator languageGenerator = new LanguageGenerator();
        Assertions.assertThat(languageGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
