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
class Sha256GeneratorTest {
    private Sha256Generator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new Sha256Generator();
    }

    @ParameterizedTest
    @CsvSource({
            "sha256,,true", "sha-256,,true", "sha_256,,true",
            ",sha256,true", ",file#sha256Hash,true", ",sha256Checksum,true", ",sha256_digest,true",
            "other,other,false", ",checksum,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerate64HexCharacters() {
        String digest = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(digest).matches("^[0-9a-f]{64}$");
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).hasSize(63);
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("not-a-sha256-digest");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("sha256", "sha-256", "sha_256");
    }
}
