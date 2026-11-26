package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class PhoneNumberGeneratorTest {

    private PhoneNumberGenerator phoneNumberGenerator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        phoneNumberGenerator = new PhoneNumberGenerator();
    }

    @Test
    void shouldGenerate() {
        Assertions.assertThat(phoneNumberGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @Test
    void shouldGenerateUSFormat() {
        StringSchema schema = new StringSchema();
        schema.setPattern("^\\+1 \\(\\d{3}\\) \\d{3}-\\d{4}$");
        Object result = phoneNumberGenerator.generate(schema);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.toString()).matches("\\+1 \\(\\d{3}\\) \\d{3}-\\d{4}");
    }

    @Test
    void shouldGenerateUKFormat() {
        StringSchema schema = new StringSchema();
        schema.setPattern("^020 \\d{4} \\d{4}$");
        Object result = phoneNumberGenerator.generate(schema);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.toString()).matches("020 \\d{4} \\d{4}");
    }

    @Test
    void shouldGenerateE164Format() {
        StringSchema schema = new StringSchema();
        schema.setFormat("e164");
        Object result = phoneNumberGenerator.generate(schema);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.toString()).matches("^\\+[1-9]\\d{10}$");
    }

    @Test
    void shouldGenerateE164WhenPatternRequiresIt() {
        StringSchema schema = new StringSchema();
        schema.setPattern("^\\+[1-9]\\d{1,14}$");
        Object result = phoneNumberGenerator.generate(schema);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.toString()).matches("^\\+[1-9]\\d{1,14}$");
    }

    @Test
    void shouldReturnNullWhenNoPatternMatches() {
        StringSchema schema = new StringSchema();
        schema.setPattern("^IMPOSSIBLE_PATTERN$");
        Object result = phoneNumberGenerator.generate(schema);
        Assertions.assertThat(result).isNull();
    }

    @ParameterizedTest
    @CsvSource({"phone,true", "e164,true", "e.164,true", "phone-number,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        Assertions.assertThat(phoneNumberGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"phone,true", "phoneNumber,true", "PHONENUMBER,true", "phoneNumber,true", "phone_number,true", "other#phoneNumber,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        Assertions.assertThat(phoneNumberGenerator.appliesTo("", property)).isEqualTo(expected);
    }

    @Test
    void shouldReturnMatchingFormats() {
        Assertions.assertThat(phoneNumberGenerator.matchingFormats())
                .isNotEmpty()
                .contains("phone", "phoneNumber");
    }
}
