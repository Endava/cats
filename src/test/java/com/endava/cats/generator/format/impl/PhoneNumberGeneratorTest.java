package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class PhoneNumberGeneratorTest {
    @Test
    void shouldGenerate() {
        PhoneNumberGenerator phoneNumberGenerator = new PhoneNumberGenerator();
        Assertions.assertThat(phoneNumberGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"phone,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        PhoneNumberGenerator phoneNumberGenerator = new PhoneNumberGenerator();
        Assertions.assertThat(phoneNumberGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"phoneNumber,true", "PHONENUMBER,true", "phoneNumber,true", "phone_number,true", "other#phoneNumber,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        PhoneNumberGenerator phoneNumberGenerator = new PhoneNumberGenerator();
        Assertions.assertThat(phoneNumberGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
