package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class NationalityGeneratorTest {

    @Test
    void shouldGenerate() {
        NationalityGenerator nationalityGenerator = new NationalityGenerator(new CountryCodeGenerator());
        Assertions.assertThat(nationalityGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(2);
    }

    @ParameterizedTest
    @CsvSource({"nationality,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        NationalityGenerator nationalityGenerator = new NationalityGenerator(new CountryCodeGenerator());
        Assertions.assertThat(nationalityGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"nationality,true", "accountNumber,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        NationalityGenerator nationalityGenerator = new NationalityGenerator(new CountryCodeGenerator());
        Assertions.assertThat(nationalityGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
