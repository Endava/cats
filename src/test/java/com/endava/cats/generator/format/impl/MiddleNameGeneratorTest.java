package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class MiddleNameGeneratorTest {
    @Test
    void shouldGenerate() {
        MiddleNameGenerator middleNameGenerator = new MiddleNameGenerator();
        Assertions.assertThat(middleNameGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"middleName,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        MiddleNameGenerator middleNameGenerator = new MiddleNameGenerator();
        Assertions.assertThat(middleNameGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"middleName,true", "MIDDLENAME,true", "middleName,true", "middle_name,true", "other#middleName,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        MiddleNameGenerator middleNameGenerator = new MiddleNameGenerator();
        Assertions.assertThat(middleNameGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
