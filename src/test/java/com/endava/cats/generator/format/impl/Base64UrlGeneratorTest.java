package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Base64;

@QuarkusTest
class Base64UrlGeneratorTest {
    private Base64UrlGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new Base64UrlGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "base64url,,true", "base64-url,,true", "BASE64URL,,true",
            "byte,,false", "binary,,false", ",base64url,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidBase64UrlWithoutPadding() {
        String value = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(value).matches("^[A-Za-z0-9_-]+$").doesNotContain("=", "+", "/");
        Assertions.assertThatNoException().isThrownBy(() -> Base64.getUrlDecoder().decode(value));
    }

    @Test
    void shouldRespectMinAndMaxLength() {
        Schema<String> maxSchema = new Schema<>();
        maxSchema.setMaxLength(8);
        String maxValue = generator.generate(maxSchema).toString();
        Assertions.assertThat(maxValue.length()).isLessThanOrEqualTo(8);

        Schema<String> minSchema = new Schema<>();
        minSchema.setMinLength(32);
        String minValue = generator.generate(minSchema).toString();
        Assertions.assertThat(minValue.length()).isGreaterThanOrEqualTo(32);
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).contains("+", "/", "=");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("$$$not-base64url$$$");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("base64url");
    }
}
