package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class IbanGeneratorTest {

    @Test
    void shouldGenerate() {
        IbanGenerator ibanGenerator = new IbanGenerator();
        Assertions.assertThat(ibanGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(5);
    }

    @ParameterizedTest
    @CsvSource({"iban,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        IbanGenerator ibanGenerator = new IbanGenerator();
        Assertions.assertThat(ibanGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"iban,true", "IBAN,true", "other#iban,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        IbanGenerator ibanGenerator = new IbanGenerator();
        Assertions.assertThat(ibanGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
