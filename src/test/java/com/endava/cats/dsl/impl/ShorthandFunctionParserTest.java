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
import java.time.ZoneId;
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
            assertThat(result).startsWith(LocalDate.now(ZoneId.systemDefault()).toString());
        }

        @Test
        @DisplayName("Should generate today's date")
        void shouldGenerateToday() {
            String result = parser.parse("#(today)", Map.of());
            assertThat(result).isEqualTo(LocalDate.now(ZoneId.systemDefault()).toString());
        }

        @Test
        @DisplayName("Should be case insensitive for no-arg functions")
        void shouldBeCaseInsensitive() {
            String result = parser.parse("#(UUID)", Map.of());
            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("Should generate ULID")
        void shouldGenerateUlid() {
            String result = parser.parse("#(ulid)", Map.of());
            assertThat(result).hasSize(26).matches("[0-9A-HJKMNP-TV-Z]{26}");
        }

        @Test
        @DisplayName("Should generate ISO country code")
        void shouldGenerateCountryCode() {
            String result = parser.parse("#(countryCode)", Map.of());
            assertThat(result).hasSize(2).matches("[A-Z]{2}");
        }

        @Test
        @DisplayName("Should replace embedded shorthands inside strings")
        void shouldReplaceEmbeddedShorthands() {
            String result = parser.parse("Rule-#(alphanumeric(6))-end", Map.of());
            assertThat(result).startsWith("Rule-").endsWith("-end").hasSize(15);
        }

        @Test
        @DisplayName("Should generate upper, lower, hex, bool, epoch, and epochMillis no-arg functions")
        void shouldGenerateAdditionalNoArgFunctions() {
            assertThat(parser.parse("#(upper)", Map.of())).hasSize(12).matches("[A-Z]{12}");
            assertThat(parser.parse("#(lower)", Map.of())).hasSize(12).matches("[a-z]{12}");
            assertThat(parser.parse("#(hex)", Map.of())).hasSize(12).matches("[0-9a-f]{12}");
            assertThat(parser.parse("#(bool)", Map.of())).isIn("true", "false");
            assertThat(parser.parse("#(boolean)", Map.of())).isIn("true", "false");
            assertThat(parser.parse("#(epoch)", Map.of())).matches("\\d{10,}");
            assertThat(parser.parse("#(epochMillis)", Map.of())).matches("\\d{13,}");
        }
    }

    @Nested
    @DisplayName("Composable string and encoding helper functions")
    class ComposableHelperFunctions {

        @Test
        @DisplayName("Should compose upper and lower with no-arg and parameterized generators")
        void shouldComposeUpperAndLower() {
            assertThat(parser.parse("#(upper(alphabetic))", Map.of())).hasSize(12).matches("[A-Z]{12}");
            assertThat(parser.parse("#(upper(alphabetic(8)))", Map.of())).hasSize(8).matches("[A-Z]{8}");
            assertThat(parser.parse("#(upper(alpha(4,8)))", Map.of())).hasSizeBetween(4, 7).matches("[A-Z]+");
            assertThat(parser.parse("#(lower(alphabetic))", Map.of())).hasSize(12).matches("[a-z]{12}");
            assertThat(parser.parse("#(lower(alphanumeric(10)))", Map.of())).hasSize(10).matches("[a-z0-9]{10}");
            assertThat(parser.parse("#(upper(uuid))", Map.of())).matches("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}");
            assertThat(parser.parse("#(upper(#(alphabetic(6))))", Map.of())).hasSize(6).matches("[A-Z]{6}");
        }

        @Test
        @DisplayName("Should support capitalize, trim, reverse, and length")
        void shouldSupportStringHelpers() {
            assertThat(parser.parse("#(capitalize(alphabetic(6)))", Map.of())).hasSize(6).matches("[A-Z][a-z]{5}");
            assertThat(parser.parse("#(capitalize(hELLO))", Map.of())).isEqualTo("Hello");
            assertThat(parser.parse("#(trim('  spaced  '))", Map.of())).isEqualTo("spaced");
            assertThat(parser.parse("#(reverse(abcd))", Map.of())).isEqualTo("dcba");
            assertThat(parser.parse("#(length(alphabetic(9)))", Map.of())).isEqualTo("9");
            assertThat(parser.parse("#(len(\"hello\"))", Map.of())).isEqualTo("5");
        }

        @Test
        @DisplayName("Should support encoding and hashing helpers")
        void shouldSupportEncodingAndHashingHelpers() {
            assertThat(parser.parse("#(base64(cats))", Map.of())).isEqualTo("Y2F0cw==");
            assertThat(parser.parse("#(base64Url(cats))", Map.of())).isEqualTo("Y2F0cw");
            assertThat(parser.parse("#(sha256(cats))", Map.of())).hasSize(64).matches("[0-9a-f]{64}");
            assertThat(parser.parse("#(md5(cats))", Map.of())).hasSize(32).matches("[0-9a-f]{32}");
            assertThat(parser.parse("#(urlEncode(a b+c@d))", Map.of())).isEqualTo("a+b%2Bc%40d");
            assertThat(parser.parse("#(upper(sha256(cats)))", Map.of())).hasSize(64).matches("[0-9A-F]{64}");
        }

        @Test
        @DisplayName("Should support context lookup and embedded shorthands inside helper functions")
        void shouldSupportContextAndEmbeddedShorthandsInsideHelpers() {
            assertThat(parser.parse("#(upper(myVar))", Map.of("myVar", "hello-cats"))).isEqualTo("HELLO-CATS");
            assertThat(parser.parse("#(upper(pre-#(alpha(4))-post))", Map.of())).matches("PRE-[A-Z]{4}-POST");
            assertThat(parser.parse("INV-#(upper(alpha(3)))-#(numeric(4))", Map.of())).matches("INV-[A-Z]{3}-\\d{4}");
        }

        @Test
        @DisplayName("Should support pick and oneOf with literals and nested functions")
        void shouldSupportPickAndOneOf() {
            assertThat(parser.parse("#(pick(USD,EUR,GBP))", Map.of())).isIn("USD", "EUR", "GBP");
            assertThat(parser.parse("#(oneOf('ALPHA', 'BETA'))", Map.of())).isIn("ALPHA", "BETA");
            assertThat(parser.parse("#(pick(upper(alpha(4)),upper(alpha(4))))", Map.of())).matches("[A-Z]{4}");
        }

        @Test
        @DisplayName("Should generate hex with fixed length and range")
        void shouldGenerateHex() {
            assertThat(parser.parse("#(hex(24))", Map.of())).hasSize(24).matches("[0-9a-f]{24}");
            assertThat(parser.parse("#(hex(4,8))", Map.of())).hasSizeBetween(4, 7).matches("[0-9a-f]+");
            assertThat(parser.parse("#(upper(hex(16)))", Map.of())).hasSize(16).matches("[0-9A-F]{16}");
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
            assertThat(result).isEqualTo(LocalDate.now(ZoneId.systemDefault()).plusDays(5).toString());
        }

        @Test
        @DisplayName("Should generate todayMinus")
        void shouldGenerateTodayMinus() {
            String result = parser.parse("#(todayMinus(10))", Map.of());
            assertThat(result).isEqualTo(LocalDate.now(ZoneId.systemDefault()).minusDays(10).toString());
        }

        @Test
        @DisplayName("Should generate nowPlusDays, nowMinusDays, nowPlusHours, nowMinusHours, and randomInt")
        void shouldGenerateNowOffsetsAndRandomInt() {
            assertThat(parser.parse("#(nowPlusDays(2))", Map.of())).contains("T");
            assertThat(parser.parse("#(nowMinusDays(2))", Map.of())).contains("T");
            assertThat(parser.parse("#(nowPlusHours(3))", Map.of())).contains("T");
            assertThat(parser.parse("#(nowMinusHours(3))", Map.of())).contains("T");
            int rand = Integer.parseInt(parser.parse("#(randomInt(10,20))", Map.of()));
            assertThat(rand).isBetween(10, 19);
            String combined = parser.parse("#(uuid)-#(alphanumeric(5))", Map.of());
            assertThat(combined).doesNotContain("#(").hasSize(36 + 1 + 5);
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

        @Test
        @DisplayName("Should handle empty args for string and pick functions and equal hex range")
        void shouldHandleEmptyArgsAndEqualHexRange() {
            assertThat(parser.parse("#(upper())", Map.of())).isEqualTo("#(upper())");
            assertThat(parser.parse("#(pick())", Map.of())).isEqualTo("#(pick())");
            assertThat(parser.parse("#(capitalize(''))", Map.of())).isEmpty();
            assertThat(parser.parse("#(hex(5,5))", Map.of())).hasSize(5).matches("[0-9a-f]{5}");
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
