package com.endava.cats.report;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ErrorSimilarityDetectorTest {

    @ParameterizedTest
    @CsvSource({
            "'Error: File not found at /path/to/file', 'Error: File not found at /another/path/to/file', true",
            "'Error: File not found at /path/to/file', 'Error: Unable to connect to database', false"
    })
    void testAreErrorsSimilar(String error1, String error2, boolean expectedSimilarity) {
        assertThat(ErrorSimilarityDetector.areErrorsSimilar(error1, error2)).isEqualTo(expectedSimilarity);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void testAreErrorsSimilar_withBlankOrNullErrors(String error1) {
        String error2 = "Error: File not found at /path/to/file";
        assertThat(ErrorSimilarityDetector.areErrorsSimilar(error1, error2)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("provideNormalizeErrorMessageTestCases")
    void testNormalizeErrorMessage(String input, String expected) {
        assertThat(ErrorSimilarityDetector.normalizeErrorMessage(input)).isEqualTo(expected);
    }

    private static Stream<Arguments> provideNormalizeErrorMessageTestCases() {
        return Stream.of(
                Arguments.of("Error 404: File not found at /path/to/file", "Error NUM: File not found at PATH"),
                Arguments.of("Error: UUID 123e4567-e89b-12d3-a456-426614174000 not found", "Error: UUID UUID not found"),
                Arguments.of("Error: Hash 1234567890abcdef1234567890abcdef12345678 not found", "Error: Hash HASH not found"),
                Arguments.of("Error: Unable to access https://example.com/resource", "Error: Unable to access URL"),
                Arguments.of("Error: File not found at /path/to/file", "Error: File not found at PATH"),
                Arguments.of("Error: Event occurred at 2023-10-01T12:34:56", "Error: Event occurred at TIMESTAMP")
        );
    }

    @org.junit.jupiter.api.Test
    void shouldExtractRootCausesAndNormalizedKeysAcrossJsonAndStackTraces() {
        String rfc7807 = "{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"detail\":\"Field 'amount' failed\",\"trace\":\"\\tat java.lang.Thread.run(Thread.java:1)\\n\\tat com.acme.OrderService.create(OrderService.java:88)\"}";
        String summary1 = ErrorSimilarityDetector.extractRootCauseSummary(rfc7807, "fallback");
        assertThat(summary1).contains("Bad Request: Field 'amount' failed @ com.acme.OrderService.create(OrderService.java:88)");

        String arrayErrors = "[{\"error\":{\"code\":\"ERR_42\"},\"message\":\"Nested error message\"}]";
        assertThat(ErrorSimilarityDetector.extractRootCauseSummary(arrayErrors, "fb")).contains("ERR_42: Nested error message");

        String violationsJson = "{\"violations\":[\"First violation\"]}";
        assertThat(ErrorSimilarityDetector.extractRootCauseSummary(violationsJson, "fb")).isEqualTo("First violation");

        String objectViolationsJson = "{\"errors\":[{\"defaultMessage\":\"Invalid field value\"}]}";
        assertThat(ErrorSimilarityDetector.extractRootCauseSummary(objectViolationsJson, "fb")).isEqualTo("Invalid field value");

        String emptyJsonObj = "{\"unrelated\":123}";
        assertThat(ErrorSimilarityDetector.extractRootCauseSummary(emptyJsonObj, "fb")).isEqualTo("{\"unrelated\":123}");

        assertThat(ErrorSimilarityDetector.extractRootCauseSummary("", "Fallback Reason")).isEqualTo("Fallback Reason");
        assertThat(ErrorSimilarityDetector.extractNormalizedClusterKey("", "Fallback Reason")).startsWith("EMPTY|");
        assertThat(ErrorSimilarityDetector.extractNormalizedClusterKey(rfc7807, "fb")).contains("bad request|");

        assertThat(ErrorSimilarityDetector.areErrorsSimilar("identical", "identical")).isTrue();
        assertThat(ErrorSimilarityDetector.areErrorsSimilar(
                "{\"error\":\"TypeA\",\"message\":\"failed\"}",
                "{\"error\":\"TypeB\",\"message\":\"failed\"}")).isFalse();
        assertThat(ErrorSimilarityDetector.wordTokenJaccard("123 !@#", "456 $%^")).isEqualTo(1.0);
        assertThat(ErrorSimilarityDetector.wordTokenJaccard("123 !@#", "valid tokens here")).isZero();
    }
}