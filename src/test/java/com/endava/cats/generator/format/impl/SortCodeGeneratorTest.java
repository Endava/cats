package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class SortCodeGeneratorTest {

    @Test
    void shouldGenerate() {
        SortCodeGenerator sortCodeGenerator = new SortCodeGenerator();
        Assertions.assertThat(sortCodeGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(5);
    }

    @ParameterizedTest
    @CsvSource({"sort-code,true", "sortCode,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        SortCodeGenerator sortCodeGenerator = new SortCodeGenerator();
        Assertions.assertThat(sortCodeGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"sortCode,true", "sort-code,true", "sort_code,true", "other,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        SortCodeGenerator sortCodeGenerator = new SortCodeGenerator();
        Assertions.assertThat(sortCodeGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
