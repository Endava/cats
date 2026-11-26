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
        for (int i = 0; i < 20; i++) {
            String generated = languageGenerator.generate(new Schema<>()).toString();
            Assertions.assertThat(generated).matches("^[a-z]{2,3}$").isNotIn("in", "iw", "ji", "mo");
        }
        Assertions.assertThat(languageGenerator.getAlmostValidValue()).isEqualTo("en-999");
        Assertions.assertThat(languageGenerator.getTotallyWrongValue()).isEqualTo("not-a-language-tag-123");
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
