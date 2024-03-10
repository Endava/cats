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
            , "not,countryCode,true", "not,country-Code,true", "not,mycountry,true", "not,not,false"})
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(countryCodeGenerator.appliesTo(format, property)).isEqualTo(expected);
    }

    @Test
    void shouldGenerate2LettersCode() {
        Schema<String> schema = new Schema<>();
        schema.setMinLength(2);
        Assertions.assertThat(countryCodeGenerator.generate(schema).toString()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"2,2", "3,3"})
    void shouldGenerateBasedOnMinLength(int schemaMinLength, int resultLength) {
        Schema<String> schema = new Schema<>();
        schema.setMinLength(schemaMinLength);
        Assertions.assertThat(countryCodeGenerator.generate(schema).toString()).hasSize(resultLength);
    }

    @ParameterizedTest
    @CsvSource({"4", "5"})
    void shouldGenerateFullWhenLargerMinLength(int schemaMinLength) {
        Schema<String> schema = new Schema<>();
        schema.setMinLength(schemaMinLength);
        Assertions.assertThat(countryCodeGenerator.generate(schema).toString()).doesNotMatch("[A-Z]{2,3}");
    }

    @ParameterizedTest
    @CsvSource({"4", "5"})
    void shouldGenerateFullWhenLargerMaxLength(int schemaMinLength) {
        Schema<String> schema = new Schema<>();
        schema.setMaxLength(schemaMinLength);
        Assertions.assertThat(countryCodeGenerator.generate(schema).toString()).hasSizeGreaterThan(4);
    }

    @ParameterizedTest
    @CsvSource({"2,2", "3,3"})
    void shouldGenerateBasedOnMaxLength(int schemaMinLength, int resultLength) {
        Schema<String> schema = new Schema<>();
        schema.setMaxLength(schemaMinLength);
        Assertions.assertThat(countryCodeGenerator.generate(schema).toString()).hasSize(resultLength);
    }

    @ParameterizedTest
    @CsvSource({"[A-Z]{2},2", "[A-Z]{3},3"})
    void shouldGenerateBasedOnPattern(String pattern, int resultLength) {
        Schema<String> schema = new Schema<>();
        schema.setPattern(pattern);
        Assertions.assertThat(countryCodeGenerator.generate(schema).toString()).hasSize(resultLength);
    }

    @Test
    void shouldGenerateFullCountry() {
        Schema<String> schema = new Schema<>();
        Assertions.assertThat(countryCodeGenerator.generate(schema)).asString().hasSizeGreaterThan(3);
    }

    @ParameterizedTest
    @CsvSource({"[a-z]+", "[a-z]{3}", "[a-z]{2}"})
    void shouldGenerateFullCountryWhenNotMatchingPattern(String pattern) {
        Schema<String> schema = new Schema<>();
        schema.setPattern(pattern);
        Assertions.assertThat(countryCodeGenerator.generate(schema).toString()).doesNotMatch("[A-Z]{2,3}");
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
