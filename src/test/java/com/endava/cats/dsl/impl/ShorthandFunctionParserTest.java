package com.endava.cats.dsl.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ShorthandFunctionParserTest {

    private ShorthandFunctionParser parser;

    @BeforeEach
    void setUp() {
        parser = new ShorthandFunctionParser();
        CatsRandom.initRandom(42L);
    }

    @Nested
    @DisplayName("No-arg functions")
    class NoArgFunctions {

        @Test
        @DisplayName("Should generate UUID")
        void shouldGenerateUuid() {
            String result = parser.parse("#(uuid)", Map.of());
            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("Should generate email")
        void shouldGenerateEmail() {
            String result = parser.parse("#(email)", Map.of());
            assertThat(result).matches("[a-z]{10}@cats\\.io");
        }

        @Test
        @DisplayName("Should generate current timestamp for now")
        void shouldGenerateNow() {
            String result = parser.parse("#(now)", Map.of());
            assertThat(result).startsWith(LocalDate.now().toString());
        }

        @Test
        @DisplayName("Should generate today's date")
        void shouldGenerateToday() {
            String result = parser.parse("#(today)", Map.of());
            assertThat(result).isEqualTo(LocalDate.now().toString());
        }

        @Test
        @DisplayName("Should be case insensitive for no-arg functions")
        void shouldBeCaseInsensitive() {
            String result = parser.parse("#(UUID)", Map.of());
            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }
    }

    @Nested
    @DisplayName("Parameterized string functions")
    class ParameterizedStringFunctions {

        @Test
        @DisplayName("Should generate alphanumeric with fixed length")
        void shouldGenerateAlphanumericFixedLength() {
            String result = parser.parse("#(alphanumeric(10))", Map.of());
            assertThat(result).hasSize(10).matches("[a-zA-Z0-9]+");
        }

        @Test
        @DisplayName("Should generate alphanumeric with range")
        void shouldGenerateAlphanumericRange() {
            String result = parser.parse("#(alphanumeric(5,15))", Map.of());
            assertThat(result).hasSizeBetween(5, 14).matches("[a-zA-Z0-9]+");
        }

        @Test
        @DisplayName("Should generate alphabetic with fixed length")
        void shouldGenerateAlphabeticFixedLength() {
            String result = parser.parse("#(alphabetic(8))", Map.of());
            assertThat(result).hasSize(8).matches("[a-zA-Z]+");
        }

        @Test
        @DisplayName("Should generate alphabetic with range")
        void shouldGenerateAlphabeticRange() {
            String result = parser.parse("#(alphabetic(3,10))", Map.of());
            assertThat(result).hasSizeBetween(3, 9).matches("[a-zA-Z]+");
        }

        @Test
        @DisplayName("Should generate numeric with fixed length")
        void shouldGenerateNumericFixedLength() {
            String result = parser.parse("#(numeric(16))", Map.of());
            assertThat(result).hasSize(16).matches("[0-9]+");
        }

        @Test
        @DisplayName("Should generate numeric with range")
        void shouldGenerateNumericRange() {
            String result = parser.parse("#(numeric(3,10))", Map.of());
            assertThat(result).hasSizeBetween(3, 9).matches("[0-9]+");
        }

        @Test
        @DisplayName("Should generate ascii with fixed length")
        void shouldGenerateAsciiFixedLength() {
            String result = parser.parse("#(ascii(12))", Map.of());
            assertThat(result).hasSize(12);
        }

        @Test
        @DisplayName("Should generate ascii with range")
        void shouldGenerateAsciiRange() {
            String result = parser.parse("#(ascii(5,20))", Map.of());
            assertThat(result).hasSizeBetween(5, 19);
        }
    }

    @Nested
    @DisplayName("Date functions")
    class DateFunctions {

        @Test
        @DisplayName("Should generate todayPlus")
        void shouldGenerateTodayPlus() {
            String result = parser.parse("#(todayPlus(5))", Map.of());
            assertThat(result).isEqualTo(LocalDate.now().plusDays(5).toString());
        }

        @Test
        @DisplayName("Should generate todayMinus")
        void shouldGenerateTodayMinus() {
            String result = parser.parse("#(todayMinus(10))", Map.of());
            assertThat(result).isEqualTo(LocalDate.now().minusDays(10).toString());
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should return original expression for unknown function")
        void shouldReturnOriginalForUnknownFunction() {
            String result = parser.parse("#(unknown(5))", Map.of());
            assertThat(result).isEqualTo("#(unknown(5))");
        }

        @Test
        @DisplayName("Should return original expression for invalid format")
        void shouldReturnOriginalForInvalidFormat() {
            String result = parser.parse("notAFunction", Map.of());
            assertThat(result).isEqualTo("notAFunction");
        }

        @Test
        @DisplayName("Should return original expression for non-numeric args")
        void shouldReturnOriginalForNonNumericArgs() {
            String result = parser.parse("#(alphanumeric(abc))", Map.of());
            assertThat(result).isEqualTo("#(alphanumeric(abc))");
        }

        @ParameterizedTest
        @ValueSource(strings = {"#(alphanumeric(10))", "#( alphanumeric(10) )", " #(alphanumeric(10)) "})
        @DisplayName("Should handle whitespace variations")
        void shouldHandleWhitespace(String expression) {
            String result = parser.parse(expression, Map.of());
            assertThat(result).hasSize(10).matches("[a-zA-Z0-9]+");
        }
    }
}
