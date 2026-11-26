package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@QuarkusTest
class HttpDateGeneratorTest {
    private HttpDateGenerator generator;

    @BeforeEach
    void setup() {
        generator = new HttpDateGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "http-date,,true", "httpDate,,true", "http_date,,true",
            ",httpDate,true", ",header#http_date,true",
            "date,,false", "date-time,,false", ",birthDate,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidRfc1123HttpDate() {
        String value = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(value).endsWith("GMT");
        Assertions.assertThatNoException().isThrownBy(() -> ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("Thu, 32 Nov 2026 18:39:21 GMT");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("not-an-http-date");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("http-date", "httpDate", "http_date");
    }
}
