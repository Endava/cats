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
class MerchantCategoryCodeGeneratorTest {
    private MerchantCategoryCodeGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new MerchantCategoryCodeGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "mcc,,true", "iso18245,,true", "merchant-category-code,,true", "merchantCategoryCode,,true",
            ",mcc,true", ",merchant#mcc,true", ",merchantCategoryCode,true", ",merchant_category_code,true",
            "other,other,false", ",currencyCode,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidFourDigitMcc() {
        for (int i = 0; i < 20; i++) {
            String mcc = generator.generate(new Schema<>()).toString();
            Assertions.assertThat(mcc).matches("^[0-9]{4}$").isNotEqualTo("0000");
        }
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("0000");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("99A9");
        Assertions.assertThat(generator.matchingFormats()).contains("mcc", "iso18245", "merchant-category-code");
    }
}
