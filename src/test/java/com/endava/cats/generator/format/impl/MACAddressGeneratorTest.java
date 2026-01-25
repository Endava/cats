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
class MACAddressGeneratorTest {

    private MACAddressGenerator generator;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        generator = new MACAddressGenerator();
    }

    @Nested
    @DisplayName("Format Matching Tests")
    class FormatMatchingTests {

        @ParameterizedTest
        @ValueSource(strings = {"mac", "MAC", "macAddress", "mac-address"})
        void shouldApplyToFormat(String format) {
            Assertions.assertThat(generator.appliesTo(format, "")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "deviceMAC",
                "networkMACAddress",
                "physicalAddress",
                "hardwareAddress"
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
        void shouldGenerateValidMACAddress() {
            Schema<String> schema = new Schema<>();
            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isInstanceOf(String.class);
            String mac = (String) result;
            Assertions.assertThat(mac).matches("([0-9A-F]{2}:){5}[0-9A-F]{2}");
        }

        @Test
        void shouldGenerateMACMatchingPattern() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");

            Object result = generator.generate(schema);

            Assertions.assertThat(result).isNotNull();
            String mac = (String) result;
            Assertions.assertThat(mac).matches("([0-9A-F]{2}:){5}[0-9A-F]{2}");
        }

        @Test
        void shouldReturnNullWhenPatternDoesNotMatch() {
            Schema<String> schema = new Schema<>();
            schema.setPattern("^\\d{10}$");

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
            Assertions.assertThat(almostValid).isEqualTo("00:1A:2B:3C:4D");
        }

        @Test
        void shouldProvideTotallyWrongValue() {
            String totallyWrong = generator.getTotallyWrongValue();

            Assertions.assertThat(totallyWrong).isNotNull();
            Assertions.assertThat(totallyWrong).isEqualTo("ZZ:ZZ:ZZ:ZZ:ZZ:ZZ");
        }
    }

    @Nested
    @DisplayName("Matching Formats Tests")
    class MatchingFormatsTests {

        @Test
        void shouldReturnMatchingFormats() {
            Assertions.assertThat(generator.matchingFormats())
                    .isNotEmpty()
                    .contains("mac", "macAddress");
        }
    }
}
