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
class DoiGeneratorTest {
    private DoiGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new DoiGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "doi,,true", "DOI,,true",
            ",doi,true", ",paper#doi,true", ",digitalObjectIdentifier,true",
            "other,other,false", ",dob,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidDoi() {
        String doi = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(doi).matches("^10\\.\\d{4,5}/[a-z0-9]{8}$");
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("11.1000/182");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("not-a-doi");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("doi");
    }
}
