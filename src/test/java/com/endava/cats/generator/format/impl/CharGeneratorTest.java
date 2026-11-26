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
class CharGeneratorTest {
    private CharGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new CharGenerator();
    }

    @ParameterizedTest
    @CsvSource({"char,,true", "CHAR,,true", ",char,false", "string,,false"})
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateSingleCharacter() {
        Assertions.assertThat(generator.generate(new Schema<>()).toString()).matches("^[A-Z]$");
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("ab");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("not-a-single-char");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("char");
    }
}
