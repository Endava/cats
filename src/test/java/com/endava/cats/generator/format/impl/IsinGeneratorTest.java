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
class IsinGeneratorTest {
    private IsinGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new IsinGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "isin,,true", "ISO-6166,,true", "iso6166,,true",
            ",isin,true", ",security#isin,true", ",isinCode,true", ",isin_code,true",
            "other,other,false", ",origin,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidIsinWithCheckDigit() {
        Assertions.assertThat(IsinGenerator.computeCheckDigit("US037833100")).isEqualTo(5);
        for (int i = 0; i < 20; i++) {
            String value = generator.generate(new Schema<>()).toString();
            Assertions.assertThat(value).matches("^[A-Z]{2}[A-Z0-9]{9}[0-9]$");
            int expectedCheck = IsinGenerator.computeCheckDigit(value.substring(0, 11));
            Assertions.assertThat(value.charAt(11) - '0').isEqualTo(expectedCheck);
        }
    }

    @Test
    void shouldRespectSchemaPattern() {
        Schema<String> schema = new Schema<>();
        schema.setPattern("^[0-9]{12}$");
        Assertions.assertThat(generator.generate(schema)).isNull();
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("US0378331006");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("INVALID_ISIN");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("isin", "iso6166");
    }
}
