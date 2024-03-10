package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class DescriptionGeneratorTest {
    @Test
    void shouldGenerate() {
        DescriptionGenerator DescriptionGenerator = new DescriptionGenerator();
        Assertions.assertThat(DescriptionGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"Description,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        DescriptionGenerator DescriptionGenerator = new DescriptionGenerator();
        Assertions.assertThat(DescriptionGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Description,true", "description,true", "other#Description,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        DescriptionGenerator DescriptionGenerator = new DescriptionGenerator();
        Assertions.assertThat(DescriptionGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
