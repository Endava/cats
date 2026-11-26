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
class SfBinaryGeneratorTest {
    private SfBinaryGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new SfBinaryGenerator();
    }

    @ParameterizedTest
    @CsvSource({"sf-binary,,true", "SF-BINARY,,true", "binary,,false", ",sfBinary,false"})
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidStructuredFieldsBinary() {
        String value = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(value).startsWith(":").endsWith(":");
        String inner = value.substring(1, value.length() - 1);
        Assertions.assertThatNoException().isThrownBy(() -> Base64.getDecoder().decode(inner));
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo(":cHJldGVuZA==");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo(":not-valid-base64$$$:");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("sf-binary");
    }
}
