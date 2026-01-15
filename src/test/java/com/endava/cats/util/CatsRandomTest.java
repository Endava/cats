package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.cornutum.regexpgen.RandomGen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class CatsRandomTest {

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(12345L);
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should initialize random with specific seed")
        void shouldInitializeRandomWithSpecificSeed() {
            CatsRandom.initRandom(42L);
            Random random = CatsRandom.instance();

            assertThat(random).isNotNull();
            assertThat(random.nextInt(100)).isEqualTo(30);
        }

        @Test
        @DisplayName("Should initialize random with zero seed using random seed")
        void shouldInitializeRandomWithZeroSeed() {
            CatsRandom.initRandom(0L);
            Random random = CatsRandom.instance();

            assertThat(random).isNotNull();
        }

        @Test
        @DisplayName("Should initialize regexp random generator")
        void shouldInitializeRegexpRandomGen() {
            CatsRandom.initRandom(42L);
            RandomGen regexpGen = CatsRandom.regexpRandomGen();

            assertThat(regexpGen).isNotNull();
        }

        @Test
        @DisplayName("Should return same instance after initialization")
        void shouldReturnSameInstance() {
            CatsRandom.initRandom(42L);
            Random random1 = CatsRandom.instance();
            Random random2 = CatsRandom.instance();

            assertThat(random1).isSameAs(random2);
        }
    }

    @Nested
    @DisplayName("Alphanumeric Generation Tests")
    class AlphanumericTests {

        @Test
        @DisplayName("Should generate alphanumeric string of specified length")
        void shouldGenerateAlphanumericString() {
            String result = CatsRandom.alphanumeric(10);

            assertThat(result).hasSize(10);
            assertThat(result).matches("[a-zA-Z0-9]+");
        }

        @Test
        @DisplayName("Should generate empty string for zero length")
        void shouldGenerateEmptyStringForZeroLength() {
            String result = CatsRandom.alphanumeric(0);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should generate deterministic alphanumeric with same seed")
        void shouldGenerateDeterministicAlphanumeric() {
            CatsRandom.initRandom(42L);
            String result1 = CatsRandom.alphanumeric(10);

            CatsRandom.initRandom(42L);
            String result2 = CatsRandom.alphanumeric(10);

            assertThat(result1).isEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("Alphabetic Generation Tests")
    class AlphabeticTests {

        @Test
        @DisplayName("Should generate alphabetic string of specified length")
        void shouldGenerateAlphabeticString() {
            String result = CatsRandom.alphabetic(10);

            assertThat(result).hasSize(10);
            assertThat(result).matches("[a-zA-Z]+");
        }

        @Test
        @DisplayName("Should generate empty string for zero length")
        void shouldGenerateEmptyStringForZeroLength() {
            String result = CatsRandom.alphabetic(0);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should generate deterministic alphabetic with same seed")
        void shouldGenerateDeterministicAlphabetic() {
            CatsRandom.initRandom(42L);
            String result1 = CatsRandom.alphabetic(10);

            CatsRandom.initRandom(42L);
            String result2 = CatsRandom.alphabetic(10);

            assertThat(result1).isEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("Numeric Generation Tests")
    class NumericTests {

        @Test
        @DisplayName("Should generate numeric string of specified length")
        void shouldGenerateNumericString() {
            String result = CatsRandom.numeric(10);

            assertThat(result).hasSize(10);
            assertThat(result).matches("[0-9]+");
        }

        @Test
        @DisplayName("Should generate empty string for zero length")
        void shouldGenerateEmptyStringForZeroLength() {
            String result = CatsRandom.numeric(0);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should generate deterministic numeric with same seed")
        void shouldGenerateDeterministicNumeric() {
            CatsRandom.initRandom(42L);
            String result1 = CatsRandom.numeric(10);

            CatsRandom.initRandom(42L);
            String result2 = CatsRandom.numeric(10);

            assertThat(result1).isEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("Numeric Range Generation Tests")
    class NumericRangeTests {

        @Test
        @DisplayName("Should generate numeric string within range")
        void shouldGenerateNumericStringWithinRange() {
            String result = CatsRandom.numeric(5, 10);

            assertThat(result).hasSizeBetween(5, 9);
            assertThat(result).matches("[0-9]+");
        }

        @Test
        @DisplayName("Should generate numeric string with equal min and max")
        void shouldGenerateNumericStringWithEqualMinMax() {
            String result = CatsRandom.numeric(5, 5);

            assertThat(result).hasSize(5);
            assertThat(result).matches("[0-9]+");
        }

        @Test
        @DisplayName("Should throw exception when max is less than min")
        void shouldThrowExceptionWhenMaxLessThanMin() {
            assertThatThrownBy(() -> CatsRandom.numeric(10, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Start value must be smaller or equal to end value.");
        }

        @Test
        @DisplayName("Should throw exception when min is negative")
        void shouldThrowExceptionWhenMinIsNegative() {
            assertThatThrownBy(() -> CatsRandom.numeric(-1, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Both range values must be non-negative.");
        }

        @Test
        @DisplayName("Should throw exception when max is negative")
        void shouldThrowExceptionWhenMaxIsNegative() {
            assertThatThrownBy(() -> CatsRandom.numeric(-5, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Both range values must be non-negative.");
        }

        @Test
        @DisplayName("Should generate zero length string when both are zero")
        void shouldGenerateZeroLengthStringWhenBothZero() {
            String result = CatsRandom.numeric(0, 0);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should generate deterministic numeric range with same seed")
        void shouldGenerateDeterministicNumericRange() {
            CatsRandom.initRandom(42L);
            String result1 = CatsRandom.numeric(5, 10);

            CatsRandom.initRandom(42L);
            String result2 = CatsRandom.numeric(5, 10);

            assertThat(result1).isEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("ASCII Generation Tests")
    class AsciiTests {

        @Test
        @DisplayName("Should generate ASCII string of specified length")
        void shouldGenerateAsciiString() {
            String result = CatsRandom.ascii(10);

            assertThat(result).hasSize(10);
            for (char c : result.toCharArray()) {
                assertThat(c).isBetween((char) 32, (char) 126);
            }
        }

        @Test
        @DisplayName("Should generate empty string for zero length")
        void shouldGenerateEmptyStringForZeroLength() {
            String result = CatsRandom.ascii(0);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should generate deterministic ASCII with same seed")
        void shouldGenerateDeterministicAscii() {
            CatsRandom.initRandom(42L);
            String result1 = CatsRandom.ascii(10);

            CatsRandom.initRandom(42L);
            String result2 = CatsRandom.ascii(10);

            assertThat(result1).isEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("Next Generation Tests")
    class NextTests {

        @Test
        @DisplayName("Should generate random string of specified length")
        void shouldGenerateRandomString() {
            String result = CatsRandom.next(10);

            assertThat(result).hasSize(10);
        }

        @Test
        @DisplayName("Should generate empty string for zero length")
        void shouldGenerateEmptyStringForZeroLength() {
            String result = CatsRandom.next(0);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should generate deterministic random string with same seed")
        void shouldGenerateDeterministicRandomString() {
            CatsRandom.initRandom(42L);
            String result1 = CatsRandom.next(10);

            CatsRandom.initRandom(42L);
            String result2 = CatsRandom.next(10);

            assertThat(result1).isEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("Instance Tests")
    class InstanceTests {

        @Test
        @DisplayName("Should return initialized random instance")
        void shouldReturnInitializedRandomInstance() {
            CatsRandom.initRandom(42L);
            Random random = CatsRandom.instance();

            assertThat(random).isNotNull();
        }

        @Test
        @DisplayName("Should return regexp random generator instance")
        void shouldReturnRegexpRandomGenInstance() {
            CatsRandom.initRandom(42L);
            RandomGen regexpGen = CatsRandom.regexpRandomGen();

            assertThat(regexpGen).isNotNull();
        }
    }
}
