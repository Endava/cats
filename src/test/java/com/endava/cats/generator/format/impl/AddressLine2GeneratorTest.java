package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class AddressLine2GeneratorTest {
    @Test
    void shouldGenerate() {
        AddressLine2Generator addressGenerator = new AddressLine2Generator();
        Assertions.assertThat(addressGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(5);
    }

    @ParameterizedTest
    @CsvSource({"line2,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        AddressLine2Generator addressGenerator = new AddressLine2Generator();
        Assertions.assertThat(addressGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"line2,true", "LINE2,true", "other,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        AddressLine2Generator addressGenerator = new AddressLine2Generator();
        Assertions.assertThat(addressGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
