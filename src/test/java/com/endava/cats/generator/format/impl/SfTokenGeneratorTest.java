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
class SfTokenGeneratorTest {
    private SfTokenGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new SfTokenGenerator();
    }

    @ParameterizedTest
    @CsvSource({"sf-token,,true", "SF-TOKEN,,true", "token,,false", ",sfToken,false"})
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidStructuredFieldsToken() {
        Assertions.assertThat(generator.generate(new Schema<>()).toString()).matches("^tok-[a-z]{6}/1$");
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("1invalid-leading-digit");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("invalid token with spaces");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("sf-token");
    }
}
