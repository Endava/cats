package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class BankAccountNumberGeneratorTest {

    private BankAccountNumberGenerator bankAccountNumberGenerator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        bankAccountNumberGenerator = new BankAccountNumberGenerator();
    }

    @Test
    void shouldGenerate() {
        Assertions.assertThat(bankAccountNumberGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(5);
    }

    @ParameterizedTest
    @CsvSource({"account-number,true", "accountNumber,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        Assertions.assertThat(bankAccountNumberGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"bankAccountNumber,true", "bankAccountAccountNumber,true", "accountNumber,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        Assertions.assertThat(bankAccountNumberGenerator.appliesTo("", property)).isEqualTo(expected);
    }

    @Test
    void shouldGenerateGermanFormat() {
        Schema<String> schema = new Schema<>();
        schema.setPattern("^\\d{10}$");
        Object result = bankAccountNumberGenerator.generate(schema);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.toString()).matches("\\d{10}");
    }

    @Test
    void shouldReturnNullWhenNoPatternMatches() {
        Schema<String> schema = new Schema<>();
        schema.setPattern("^[A-Z]{50}$");
        Object result = bankAccountNumberGenerator.generate(schema);
        Assertions.assertThat(result).isNull();
    }

    @Test
    void shouldReturnMatchingFormats() {
        Assertions.assertThat(bankAccountNumberGenerator.matchingFormats())
                .isNotEmpty()
                .contains("account-number", "accountNumber");
    }
}
