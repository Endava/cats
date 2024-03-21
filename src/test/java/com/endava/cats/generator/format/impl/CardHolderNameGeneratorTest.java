package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CardHolderNameGeneratorTest {
    @Test
    void shouldGenerate() {
        CardholderNameGenerator cardholderNameGenerator = new CardholderNameGenerator();
        Assertions.assertThat(cardholderNameGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"person-name,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        CardholderNameGenerator cardholderNameGenerator = new CardholderNameGenerator();
        Assertions.assertThat(cardholderNameGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"cardholderName,true", "CARDHOLDERNAME,true", "cardholderName,true", "card_holder_name,true", "other#cardholderName,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        CardholderNameGenerator cardholderNameGenerator = new CardholderNameGenerator();
        Assertions.assertThat(cardholderNameGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
