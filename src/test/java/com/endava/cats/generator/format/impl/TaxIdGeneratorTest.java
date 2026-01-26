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
class TaxIdGeneratorTest {

    private TaxIdGenerator generator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        generator = new TaxIdGenerator();
    }

    @Nested
    @DisplayName("Format Matching Tests")
    class FormatMatchingTests {

        @ParameterizedTest
        @ValueSource(strings = {"taxid", "ein", "tin", "utr", "siren", "siret", "nif"})
        void shouldApplyToFormat(String format) {
            Assertions.assertThat(generator.appliesTo(format, "")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "companyTaxId",
                "employerEIN",
                "businessTIN",
                "ukUTR",
                "frenchSIREN",
                "frenchSIRET",
                "spanishNIF",
                "italianCodiceFiscale",
                "employerIdentificationNumber"
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
        void shouldGenerateValidTaxId() {
            Schema<String> schema = new Schema<>();
            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull().isInstanceOf(String.class);
            String taxId = (String) result;
            Assertions.assertThat(taxId).isNotEmpty();
        }

        @Test
        void shouldGenerateUSEINMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{2}-\\d{7}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String ein = (String) result;
            Assertions.assertThat(ein).matches("\\d{2}-\\d{7}");
        }

        @Test
        void shouldGenerateUKUTRMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{10}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String utr = (String) result;
            Assertions.assertThat(utr).matches("\\d{10}");
        }

        @Test
        void shouldGenerateFrenchSIRENMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{9}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String siren = (String) result;
            Assertions.assertThat(siren).matches("\\d{9}");
        }

        @Test
        void shouldGenerateFrenchSIRETMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{14}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String siret = (String) result;
            Assertions.assertThat(siret).matches("\\d{14}");
        }

        @Test
        void shouldGenerateSpanishNIFMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{8}[A-Z]$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String nif = (String) result;
            Assertions.assertThat(nif).matches("\\d{8}[A-Z]");
        }

        @Test
        void shouldGenerateItalianCodiceFiscaleMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String cf = (String) result;
            Assertions.assertThat(cf).matches("[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]");
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
    @DisplayName("Invalid Data Tests")
    class InvalidDataTests {

        @Test
        void shouldProvideAlmostValidValue() {
            String almostValid = generator.getAlmostValidValue();

            Assertions.assertThat(almostValid).isNotNull().isNotEmpty();
        }

        @Test
        void shouldProvideTotallyWrongValue() {
            String totallyWrong = generator.getTotallyWrongValue();

            Assertions.assertThat(totallyWrong).isNotNull().isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Matching Formats Tests")
    class MatchingFormatsTests {

        @Test
        void shouldReturnMatchingFormats() {
            Assertions.assertThat(generator.matchingFormats())
                    .isNotEmpty()
                    .contains("taxId", "ein", "tin", "utr", "siren", "siret", "nif");
        }
    }
}
