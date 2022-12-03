package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CurrencyCodeGeneratorTest {

    private CurrencyCodeGenerator currencyCodeGenerator;

    @BeforeEach
    void setup() {
        currencyCodeGenerator = new CurrencyCodeGenerator();
    }

    @ParameterizedTest
    @CsvSource({"iso-4217,not,true", "currency-code,not,true", "currencyCode,not,true", "not,currencycode,true", "not,currency-code,true"
            , "not,currencyCode,true", "not,currency-Code,true", "not,not,false"})
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(currencyCodeGenerator.appliesTo(format, property)).isEqualTo(expected);
    }

    @Test
    void shouldGenerate() {
        Assertions.assertThat(currencyCodeGenerator.generate(new Schema<>()).toString()).hasSize(3);
    }

    @Test
    void shouldGenerateWrongValue() {
        Assertions.assertThat(currencyCodeGenerator.getAlmostValidValue()).isEqualTo("ROL");

    }

    @Test
    void shouldGenerateTotallyWrongValue() {
        Assertions.assertThat(currencyCodeGenerator.getTotallyWrongValue()).isEqualTo("XXX");
    }
}
