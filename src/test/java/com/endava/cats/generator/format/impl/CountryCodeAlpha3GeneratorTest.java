package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CountryCodeAlpha3GeneratorTest {

    @Test
    void shouldGenerate() {
        CountryCodeAlpha3Generator countryCodeAlpha2Generator = new CountryCodeAlpha3Generator();
        Assertions.assertThat(countryCodeAlpha2Generator.generate(new Schema<>()).toString()).hasSize(3);
    }

    @ParameterizedTest
    @CsvSource({"iso-3166-alpha-3,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        CountryCodeAlpha3Generator countryCodeAlpha2Generator = new CountryCodeAlpha3Generator();
        Assertions.assertThat(countryCodeAlpha2Generator.appliesTo(format, "")).isEqualTo(expected);
    }

    @Test
    void shouldGenerateWrongValue() {
        CountryCodeAlpha3Generator countryCodeAlpha2Generator = new CountryCodeAlpha3Generator();
        Assertions.assertThat(countryCodeAlpha2Generator.getAlmostValidValue()).isEqualTo("ROM");
    }

    @Test
    void shouldGenerateTotallyWrongValue() {
        CountryCodeAlpha3Generator countryCodeAlpha2Generator = new CountryCodeAlpha3Generator();
        Assertions.assertThat(countryCodeAlpha2Generator.getTotallyWrongValue()).isEqualTo("XXX");
    }
}
