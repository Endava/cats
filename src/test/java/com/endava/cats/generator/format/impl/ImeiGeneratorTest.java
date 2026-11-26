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
class ImeiGeneratorTest {
    private ImeiGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new ImeiGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "imei,,true", "IMEI,,true",
            ",imei,true", ",device#imei,true", ",imeiNumber,true", ",imei_number,true",
            "other,other,false", ",phoneNumber,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidLuhnImei() {
        Assertions.assertThat(ImeiGenerator.computeLuhnCheckDigit("49015420323751")).isEqualTo(8);
        for (int i = 0; i < 20; i++) {
            String imei = generator.generate(new Schema<>()).toString();
            Assertions.assertThat(imei).matches("^[0-9]{15}$");
            int expectedCheck = ImeiGenerator.computeLuhnCheckDigit(imei.substring(0, 14));
            Assertions.assertThat(imei.charAt(14) - '0').isEqualTo(expectedCheck);
        }
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("490154203237519");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("IMEI-INVALID-12");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("imei");
    }
}
