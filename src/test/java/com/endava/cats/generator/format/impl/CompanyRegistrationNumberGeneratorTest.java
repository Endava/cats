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
class CompanyRegistrationNumberGeneratorTest {

    private CompanyRegistrationNumberGenerator generator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        generator = new CompanyRegistrationNumberGenerator();
    }

    @Nested
    @DisplayName("Format Matching Tests")
    class FormatMatchingTests {

        @ParameterizedTest
        @ValueSource(strings = {"companynumber", "companyNumber", "companyRegistrationNumber"})
        void shouldApplyToFormat(String format) {
            Assertions.assertThat(generator.appliesTo(format, "")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "businessCompanyNumber",
                "organizationCompanyRegistrationNumber",
                "entityRegistrationNumber",
                "companyRegNo"
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
        void shouldGenerateValidCompanyNumber() {
            Schema<String> schema = new Schema<>();
            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull().isInstanceOf(String.class);
        }

        @ParameterizedTest
        @CsvSource({"^[A-Z]{2}\\d{6}$,[A-Z]{2}\\d{6}", "^HRB \\d{5}$,HRB \\d{5}", "^\\d{9}$,\\d{9}", "^J\\d{2}/\\d{4}/\\d{4}$,J\\d{2}/\\d{4}/\\d{4}"})
        void shouldGenerateCompanyNumberMatchingPattern(String sourceRegex, String matchesRegex) {
            Schema<String> schema = new Schema<>();
            schema.setPattern(sourceRegex);

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String companyNumber = (String) result;
            Assertions.assertThat(companyNumber).matches(matchesRegex);
        }


        @Test
        void shouldReturnNullWhenPatternDoesNotMatch() {
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
                    .contains("companyNumber", "companyRegistrationNumber");
        }
    }
}
