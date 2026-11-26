package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

@QuarkusTest
class DecimalStringGeneratorTest {
    private DecimalStringGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new DecimalStringGenerator();
    }

    @ParameterizedTest
    @CsvSource({"decimal,,true", "decimal128,,true", "DECIMAL,,true", ",decimal,false", "double,,false"})
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidDecimalString() {
        String value = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(value).matches("^\\d+\\.\\d{2}$");
        Assertions.assertThatNoException().isThrownBy(() -> new BigDecimal(value));
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("123.45.67");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("not-a-decimal");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("decimal", "decimal128");
    }
}
