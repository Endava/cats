package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CardNumberGeneratorTest {

    @Test
    void shouldGenerate() {
        CardNumberGenerator cardNumberGenerator = new CardNumberGenerator();
        String generated = cardNumberGenerator.generate(new Schema<>()).toString();
        Assertions.assertThat(generated).matches(CardNumberGenerator.CARDS::contains);
    }

    @ParameterizedTest
    @CsvSource({"card-number,not,true", "card#number,not,true", "not,card-number,true", "not,not,false"})
    void shouldApply(String format, String property, boolean expected) {
        CardNumberGenerator cardNumberGenerator = new CardNumberGenerator();
        Assertions.assertThat(cardNumberGenerator.appliesTo(format, property)).isEqualTo(expected);
    }

    @Test
    void shouldGenerateWrongValue() {
        CardNumberGenerator cardNumberGenerator = new CardNumberGenerator();
        Assertions.assertThat(cardNumberGenerator.getAlmostValidValue()).isEqualTo("2222420000001112");
    }

    @Test
    void shouldGenerateTotallyWrongValue() {
        CardNumberGenerator cardNumberGenerator = new CardNumberGenerator();
        Assertions.assertThat(cardNumberGenerator.getTotallyWrongValue()).isEqualTo("4444444444444444");
    }
}
