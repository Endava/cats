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
class IPAddressRangeGeneratorTest {

    private IPAddressRangeGenerator generator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        generator = new IPAddressRangeGenerator();
    }

    @Nested
    @DisplayName("Format Matching Tests")
    class FormatMatchingTests {

        @ParameterizedTest
        @ValueSource(strings = {"cidr", "CIDR", "ipRange"})
        void shouldApplyToFormat(String format) {
            Assertions.assertThat(generator.appliesTo(format, "")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "networkCIDR",
                "subnetIPRange",
                "addressSubnet",
                "networkRange"
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
        void shouldGenerateValidCIDR() {
            Schema<String> schema = new Schema<>();
            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isInstanceOf(String.class);
            String cidr = (String) result;
            Assertions.assertThat(cidr).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}");
        }

        @Test
        void shouldGenerateCIDRMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String cidr = (String) result;
            Assertions.assertThat(cidr).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}");
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
    @DisplayName("Matching Formats Tests")
    class MatchingFormatsTests {

        @Test
        void shouldReturnMatchingFormats() {
            Assertions.assertThat(generator.matchingFormats())
                    .isNotEmpty()
                    .contains("cidr", "ipRange", "subnet");
        }
    }
}
