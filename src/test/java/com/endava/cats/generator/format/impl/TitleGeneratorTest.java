package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class TitleGeneratorTest {

    @Test
    void shouldGenerate() {
        TitleGenerator titleGenerator = new TitleGenerator();
        Assertions.assertThat(titleGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"title,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        TitleGenerator titleGenerator = new TitleGenerator();
        Assertions.assertThat(titleGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"title,true", "TITLE,true", "bookTitle,true", "book_title,true", "other#title,true", "other,false", "jobTitle,false", "job_title,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        TitleGenerator titleGenerator = new TitleGenerator();
        Assertions.assertThat(titleGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
