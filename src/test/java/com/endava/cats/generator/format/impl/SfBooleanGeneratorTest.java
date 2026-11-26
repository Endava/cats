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
class SfBooleanGeneratorTest {
    private SfBooleanGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new SfBooleanGenerator();
    }

    @ParameterizedTest
    @CsvSource({"sf-boolean,,true", "SF-BOOLEAN,,true", "boolean,,false", ",sfBoolean,false"})
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidStructuredFieldsBoolean() {
        Assertions.assertThat(generator.generate(new Schema<>()).toString()).isIn("?0", "?1");
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("?2");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("true");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("sf-boolean");
    }
}
