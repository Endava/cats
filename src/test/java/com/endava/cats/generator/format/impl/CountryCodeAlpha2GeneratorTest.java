package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.impl.CountryCodeAlpha2Generator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CountryCodeAlpha2GeneratorTest {

    @Test
    void shouldGenerate() {
        CountryCodeAlpha2Generator countryCodeAlpha2Generator = new CountryCodeAlpha2Generator();
        Assertions.assertThat(countryCodeAlpha2Generator.generate(new Schema<>()).toString()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"iso-3166-alpha-2,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        CountryCodeAlpha2Generator countryCodeAlpha2Generator = new CountryCodeAlpha2Generator();
        Assertions.assertThat(countryCodeAlpha2Generator.appliesTo(format, "")).isEqualTo(expected);
    }
}
