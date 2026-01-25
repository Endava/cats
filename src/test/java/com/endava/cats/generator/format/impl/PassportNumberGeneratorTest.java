package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class PassportNumberGeneratorTest {

    private PassportNumberGenerator generator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        generator = new PassportNumberGenerator();
    }

    @Nested
    @DisplayName("Format Matching Tests")
    class FormatMatchingTests {

        @ParameterizedTest
        @ValueSource(strings = {"passport", "PASSPORT", "passportNumber"})
        void shouldApplyToFormat(String format) {
            Assertions.assertThat(generator.appliesTo(format, "")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "userPassport",
                "customerPassportNumber",
                "travellerPassportNo"
        })
        void shouldApplyToPropertyName(String propertyName) {
            Assertions.assertThat(generator.appliesTo("", propertyName)).isTrue();
        }

        @Test
        void shouldNotApplyToUnrelatedFormat() {
            Assertions.assertThat(generator.appliesTo("email", "username")).isFalse();
        }
    }

    @Nested
    @DisplayName("Generation Tests")
    class GenerationTests {

        @Test
        void shouldGenerateValidPassport() {
            Schema<String> schema = new Schema<>();
            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isInstanceOf(String.class);
            String passport = (String) result;
            Assertions.assertThat(passport).isNotEmpty();
        }

        @Test
        void shouldGenerateUSPassportMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{9}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String passport = (String) result;
            Assertions.assertThat(passport).matches("\\d{9}");
        }

        @Test
        void shouldGenerateUSNewPassportMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^[A-Z]{2}\\d{7}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String passport = (String) result;
            Assertions.assertThat(passport).matches("[A-Z]{2}\\d{7}");
        }

        @Test
        void shouldGenerateGermanPassportMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^C[A-Z0-9]{8}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String passport = (String) result;
            Assertions.assertThat(passport).matches("C[A-Z0-9]{8}");
        }

        @Test
        void shouldReturnNullWhenNoPatternMatches() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^IMPOSSIBLE_PATTERN$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Matching Formats Tests")
    class MatchingFormatsTests {

        @Test
        void shouldReturnMatchingFormats() {
            Assertions.assertThat(generator.matchingFormats())
                    .isNotEmpty()
                    .contains("passport", "passportNumber");
        }
    }
}
