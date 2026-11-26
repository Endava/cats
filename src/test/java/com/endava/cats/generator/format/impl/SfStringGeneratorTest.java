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
class SfStringGeneratorTest {
    private SfStringGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new SfStringGenerator();
    }

    @ParameterizedTest
    @CsvSource({"sf-string,,true", "SF-STRING,,true", "string,,false", ",sfString,false"})
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidStructuredFieldsString() {
        Assertions.assertThat(generator.generate(new Schema<>()).toString()).matches("^\"[A-Za-z0-9]{8}\"$");
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("\"unterminated");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("unquoted-string");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("sf-string");
    }
}
