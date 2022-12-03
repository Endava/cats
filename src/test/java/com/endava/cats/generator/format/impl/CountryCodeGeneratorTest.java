package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CountryCodeGeneratorTest {

    private CountryCodeGenerator countryCodeGenerator;

    @BeforeEach
    void setup() {
        countryCodeGenerator = new CountryCodeGenerator();
    }

    @ParameterizedTest
    @CsvSource({"iso-3166,not,true", "country-code,not,true", "countryCode,not,true", "not,countrycode,true", "not,country-code,true"
            , "not,countryCode,true", "not,country-Code,true", "not,not,false"})
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(countryCodeGenerator.appliesTo(format, property)).isEqualTo(expected);
    }

    @Test
    void shouldGenerate2LettersCode() {
        Schema<String> schema = new Schema<>();
        schema.setMinLength(2);
        Assertions.assertThat(countryCodeGenerator.generate(schema).toString()).hasSize(2);
    }

    @Test
    void shouldGenerate3LettersCodeWhenHavingMinLength() {
        Schema<String> schema = new Schema<>();
        schema.setMinLength(4);
        Assertions.assertThat(countryCodeGenerator.generate(schema).toString()).hasSize(3);
    }

    @Test
    void shouldGenerate3LettersCode() {
        Assertions.assertThat(countryCodeGenerator.generate(new Schema<>()).toString()).hasSize(3);
    }

    @Test
    void shouldGenerateWrongValue() {
        Assertions.assertThat(countryCodeGenerator.getAlmostValidValue()).isEqualTo("ROM");
    }

    @Test
    void shouldGenerateTotallyWrongValue() {
        Assertions.assertThat(countryCodeGenerator.getTotallyWrongValue()).isEqualTo("XXX");
    }
}
