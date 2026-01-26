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
class SSNGeneratorTest {

    private SSNGenerator generator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        generator = new SSNGenerator();
    }

    @Nested
    @DisplayName("Format Matching Tests")
    class FormatMatchingTests {

        @ParameterizedTest
        @ValueSource(strings = {"ssn", "SSN", "nin", "NIN", "bsn", "BSN", "personnummer", "PERSONNUMMER"})
        void shouldApplyToFormat(String format) {
            Assertions.assertThat(generator.appliesTo(format, "")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "customerSSN",
                "userSocialSecurityNumber",
                "employeeNIN",
                "nationalInsuranceNumber",
                "citizenBSN",
                "personPersonnummer",
                "socialInsuranceNumber"
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
        void shouldGenerateValidSSN() {
            Schema<String> schema = new Schema<>();
            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull().isInstanceOf(String.class);
            String ssn = (String) result;
            Assertions.assertThat(ssn).isNotEmpty();
        }

        @Test
        void shouldGenerateSSNMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{3}-\\d{2}-\\d{4}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String ssn = (String) result;
            Assertions.assertThat(ssn).matches("\\d{3}-\\d{2}-\\d{4}");
        }

        @Test
        void shouldGenerateUKNIMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^[A-Z]{2}\\d{6}[A-Z]$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String ni = (String) result;
            Assertions.assertThat(ni).matches("[A-Z]{2}\\d{6}[A-Z]");
        }

        @Test
        void shouldGenerateDutchBSNWithValidChecksum() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{9}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String bsn = (String) result;
            Assertions.assertThat(bsn).matches("\\d{9}");

            // Verify BSN checksum (11-proof)
            int sum = 0;
            for (int i = 0; i < 8; i++) {
                sum += Character.getNumericValue(bsn.charAt(i)) * (9 - i);
            }
            int checkDigit = sum % 11;
            if (checkDigit == 10) {
                checkDigit = 0;
            }
            Assertions.assertThat(Character.getNumericValue(bsn.charAt(8))).isEqualTo(checkDigit);
        }

        @Test
        void shouldGenerateSwedishPersonnummerMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{6}-\\d{4}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String personnummer = (String) result;
            Assertions.assertThat(personnummer).matches("\\d{6}-\\d{4}");
        }

        @Test
        void shouldReturnNullWhenNoPatternMatches() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^IMPOSSIBLE_PATTERN$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNull();
        }

        @Test
        void shouldRespectMaxLength() {
            Schema<String> schema = new Schema<>();
            schema.setMaxLength(5);

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

        @Test
        void shouldProvideAlmostValidValueThatIsInvalid() {
            String almostValid = generator.getAlmostValidValue();

            // Should not match valid US SSN pattern
            Assertions.assertThat(almostValid).doesNotMatch("^[1-9]\\d{2}-[1-9]\\d-\\d{4}$");
        }
    }

    @Nested
    @DisplayName("Matching Formats Tests")
    class MatchingFormatsTests {

        @Test
        void shouldReturnMatchingFormats() {
            Assertions.assertThat(generator.matchingFormats())
                    .isNotEmpty()
                    .contains("ssn", "nin", "bsn", "personnummer");
        }
    }
}
