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
class SemVerGeneratorTest {

    private SemVerGenerator generator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        generator = new SemVerGenerator();
    }

    @Nested
    @DisplayName("Format Matching Tests")
    class FormatMatchingTests {

        @ParameterizedTest
        @ValueSource(strings = {"semver", "SEMVER", "version", "VERSION"})
        void shouldApplyToFormat(String format) {
            Assertions.assertThat(generator.appliesTo(format, "")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "appVersion",
                "apiVersion",
                "softwareVersion",
                "packageSemver"
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
        void shouldGenerateValidSemVer() {
            Schema<String> schema = new Schema<>();
            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isInstanceOf(String.class);
            String semver = (String) result;
            Assertions.assertThat(semver).matches("\\d+\\.\\d+\\.\\d+");
        }

        @Test
        void shouldGenerateSemVerMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d+\\.\\d+\\.\\d+$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String semver = (String) result;
            Assertions.assertThat(semver).matches("\\d+\\.\\d+\\.\\d+");
        }

        @Test
        void shouldReturnNullWhenPatternDoesNotMatch() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^[A-Z]{10}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Invalid Data Tests")
    class InvalidDataTests {

        @Test
        void shouldProvideAlmostValidValue() {
            String almostValid = generator.getAlmostValidValue();

            Assertions.assertThat(almostValid).isNotNull();
            Assertions.assertThat(almostValid).isEqualTo("1.2");
        }

        @Test
        void shouldProvideTotallyWrongValue() {
            String totallyWrong = generator.getTotallyWrongValue();

            Assertions.assertThat(totallyWrong).isNotNull();
            Assertions.assertThat(totallyWrong).isEqualTo("v1.2.3.4.5");
        }
    }

    @Nested
    @DisplayName("Matching Formats Tests")
    class MatchingFormatsTests {

        @Test
        void shouldReturnMatchingFormats() {
            Assertions.assertThat(generator.matchingFormats())
                    .isNotEmpty()
                    .contains("semver", "version");
        }
    }
}
