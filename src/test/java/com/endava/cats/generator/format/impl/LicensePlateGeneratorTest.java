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
class LicensePlateGeneratorTest {

    private LicensePlateGenerator generator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        generator = new LicensePlateGenerator();
    }

    @Nested
    @DisplayName("Format Matching Tests")
    class FormatMatchingTests {

        @ParameterizedTest
        @ValueSource(strings = {"licenseplate", "licensePlate", "numberPlate"})
        void shouldApplyToFormat(String format) {
            Assertions.assertThat(generator.appliesTo(format, "")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "vehicleLicensePlate",
                "carNumberPlate",
                "registrationPlateNumber"
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
        void shouldGenerateValidLicensePlate() {
            Schema<String> schema = new Schema<>();
            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isInstanceOf(String.class);
            String plate = (String) result;
            Assertions.assertThat(plate).isNotEmpty();
        }

        @Test
        void shouldGenerateUSPlateMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{3}-[A-Z]{3}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String plate = (String) result;
            Assertions.assertThat(plate).matches("\\d{3}-[A-Z]{3}");
        }

        @Test
        void shouldGenerateUKPlateMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^[A-Z]{2}\\d{2} [A-Z]{3}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String plate = (String) result;
            Assertions.assertThat(plate).matches("[A-Z]{2}\\d{2} [A-Z]{3}");
        }

        @Test
        void shouldGenerateFrenchPlateMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^[A-Z]{2}-\\d{3}-[A-Z]{2}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String plate = (String) result;
            Assertions.assertThat(plate).matches("[A-Z]{2}-\\d{3}-[A-Z]{2}");
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
                    .contains("licensePlate", "numberPlate");
        }
    }
}
