package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class BicGeneratorTest {
    @Test
    void shouldGenerate() {
        BicGenerator bicGenerator = new BicGenerator();
        Assertions.assertThat(bicGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"bic,true", "bic,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        BicGenerator bicGenerator = new BicGenerator();
        Assertions.assertThat(bicGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"bic,true", "BIC,true", "swift,true", "bic_code,true", "other#swift_code,true", "other#bank_identifier_code,true",
            "other#bank_identifier,true", "other#bank_code,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        BicGenerator bicGenerator = new BicGenerator();
        Assertions.assertThat(bicGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
