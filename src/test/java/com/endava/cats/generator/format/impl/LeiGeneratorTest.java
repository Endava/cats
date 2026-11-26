package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;

@QuarkusTest
class LeiGeneratorTest {
    private LeiGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new LeiGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "lei,,true", "iso17442,,true", "ISO-17442,,true",
            ",lei,true", ",entity#lei,true", ",leiCode,true", ",legalEntityIdentifier,true", ",legal_entity_identifier,true",
            "other,other,false", ",fileId,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidLeiWithMod97CheckDigits() {
        Assertions.assertThat(LeiGenerator.computeCheckDigits("5493001KJTIIGC8Y1R")).isEqualTo("12");
        for (int i = 0; i < 20; i++) {
            String lei = generator.generate(new Schema<>()).toString();
            Assertions.assertThat(lei).matches("^[A-Z0-9]{4}00[A-Z0-9]{12}[0-9]{2}$");
            StringBuilder expanded = new StringBuilder();
            for (int j = 0; j < lei.length(); j++) {
                char c = lei.charAt(j);
                if (Character.isLetter(c)) {
                    expanded.append(c - 'A' + 10);
                } else {
                    expanded.append(c);
                }
            }
            Assertions.assertThat(new BigInteger(expanded.toString()).mod(BigInteger.valueOf(97)))
                    .isEqualTo(BigInteger.ONE);
        }
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("5493001KJTIIGC8Y1R00");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("NOT-A-VALID-LEI");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("lei", "iso17442");
    }
}
