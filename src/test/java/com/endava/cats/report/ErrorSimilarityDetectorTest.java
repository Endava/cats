package com.endava.cats.report;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ErrorSimilarityDetectorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "[Empty response body]"})
    void shouldTreatBlankBodiesAsEmpty(String body) {
        assertThat(ErrorSimilarityDetector.isBlankOrEmptyPlaceholder(body)).isTrue();
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

    @Test
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

        // Cover extractCause on blank, non-object JSON array and http:// errorType
        assertThat(ErrorSimilarityDetector.extractCause("")).isEqualTo(new ErrorSimilarityDetector.ExtractedCause("", "", ""));
        assertThat(ErrorSimilarityDetector.extractRootCauseSummary("[1, 2]", "fb")).isEqualTo("[1, 2]");
        assertThat(ErrorSimilarityDetector.extractRootCauseSummary("{\"type\":\"https://errors.example/bad\",\"title\":\"Custom Title\"}", "fb"))
                .isEqualTo("Custom Title");
        assertThat(ErrorSimilarityDetector.extractRootCauseSummary("", null)).isEqualTo("[Empty response body]");
    }
}