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
class UlidGeneratorTest {
    private UlidGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new UlidGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "ulid,,true", "ULID,,true",
            ",ulid,true", ",event#ulid,true", ",requestUlid,true",
            "uuid,,false", "other,other,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidCrockfordBase32Ulid() {
        for (int i = 0; i < 20; i++) {
            String ulid = generator.generate(new Schema<>()).toString();
            Assertions.assertThat(ulid).matches("^[0-7][0-9A-HJKMNP-TV-Z]{25}$");
        }
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).contains("U").hasSize(26);
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("not-a-ulid-value");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("ulid");
    }
}
