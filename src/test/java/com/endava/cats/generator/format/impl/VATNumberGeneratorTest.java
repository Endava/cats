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
class VATNumberGeneratorTest {

    private VATNumberGenerator generator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        generator = new VATNumberGenerator();
    }

    @Nested
    @DisplayName("Format Matching Tests")
    class FormatMatchingTests {

        @ParameterizedTest
        @ValueSource(strings = {"vat", "VAT", "vatNumber", "gst", "GST"})
        void shouldApplyToFormat(String format) {
            Assertions.assertThat(generator.appliesTo(format, "")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "companyVAT",
                "businessVATNumber",
                "taxGST",
                "gstNumber",
                "taxNumber"
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
        void shouldGenerateValidVATNumber() {
            Schema<String> schema = new Schema<>();
            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isInstanceOf(String.class);
            String vat = (String) result;
            Assertions.assertThat(vat).isNotEmpty();
            Assertions.assertThat(vat).hasSizeGreaterThanOrEqualTo(10);
        }

        @Test
        void shouldGenerateVATWithCountryCode() {
            Schema<String> schema = new Schema<>();
            Object result = generator.generate(schema);

            String vat = (String) result;
            Assertions.assertThat(vat.substring(0, 2)).matches("[A-Z]{2}");
        }

        @Test
        void shouldGenerateVATMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^[A-Z]{2}\\d{8,10}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String vat = (String) result;
            Assertions.assertThat(vat).matches("[A-Z]{2}\\d{8,10}");
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

            Assertions.assertThat(almostValid).isNotNull();
            Assertions.assertThat(almostValid).isEqualTo("GB12345678");
        }

        @Test
        void shouldProvideTotallyWrongValue() {
            String totallyWrong = generator.getTotallyWrongValue();

            Assertions.assertThat(totallyWrong).isNotNull();
            Assertions.assertThat(totallyWrong).isEqualTo("XX000000000");
        }
    }

    @Nested
    @DisplayName("Matching Formats Tests")
    class MatchingFormatsTests {

        @Test
        void shouldReturnMatchingFormats() {
            Assertions.assertThat(generator.matchingFormats())
                    .isNotEmpty()
                    .contains("vat", "vatNumber", "gst", "gstNumber");
        }
    }
}
