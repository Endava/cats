package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class BankAccountNumberGeneratorTest {

    @Test
    void shouldGenerate() {
        BankAccountNumberGenerator bankAccountNumberGenerator = new BankAccountNumberGenerator();
        Assertions.assertThat(bankAccountNumberGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(5);
    }

    @ParameterizedTest
    @CsvSource({"account-number,true", "accountNumber,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        BankAccountNumberGenerator bankAccountNumberGenerator = new BankAccountNumberGenerator();
        Assertions.assertThat(bankAccountNumberGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"bankAccountNumber,true", "bankAccountAccountNumber,true", "accountNumber,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        BankAccountNumberGenerator bankAccountNumberGenerator = new BankAccountNumberGenerator();
        Assertions.assertThat(bankAccountNumberGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
