package com.endava.cats.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorSimilarityDetectorTest {

    @Test
    void testAreErrorsSimilar_withSimilarErrors() {
        String error1 = "Error: File not found at /path/to/file";
        String error2 = "Error: File not found at /another/path/to/file";

        assertThat(ErrorSimilarityDetector.areErrorsSimilar(error1, error2)).isTrue();
    }

    @Test
    void testAreErrorsSimilar_withDifferentErrors() {
        String error1 = "Error: File not found at /path/to/file";
        String error2 = "Error: Unable to connect to database";

        assertThat(ErrorSimilarityDetector.areErrorsSimilar(error1, error2)).isFalse();
    }

    @Test
    void testAreErrorsSimilar_withBlankErrors() {
        String error1 = "";
        String error2 = "Error: File not found at /path/to/file";

        assertThat(ErrorSimilarityDetector.areErrorsSimilar(error1, error2)).isFalse();
    }

    @Test
    void testAreErrorsSimilar_withNullErrors() {
        String error1 = null;
        String error2 = "Error: File not found at /path/to/file";

        assertThat(ErrorSimilarityDetector.areErrorsSimilar(error1, error2)).isFalse();
    }

    @Test
    void testNormalizeErrorMessage_withNumbers() {
        String message = "Error 404: File not found at /path/to/file";
        String expected = "Error NUM: File not found at PATH";

        assertThat(ErrorSimilarityDetector.normalizeErrorMessage(message)).isEqualTo(expected);
    }

    @Test
    void testNormalizeErrorMessage_withUUID() {
        String message = "Error: UUID 123e4567-e89b-12d3-a456-426614174000 not found";
        String expected = "Error: UUID UUID not found";

        assertThat(ErrorSimilarityDetector.normalizeErrorMessage(message)).isEqualTo(expected);
    }

    @Test
    void testNormalizeErrorMessage_withHash() {
        String message = "Error: Hash 1234567890abcdef1234567890abcdef12345678 not found";
        String expected = "Error: Hash HASH not found";

        assertThat(ErrorSimilarityDetector.normalizeErrorMessage(message)).isEqualTo(expected);
    }

    @Test
    void testNormalizeErrorMessage_withURL() {
        String message = "Error: Unable to access https://example.com/resource";
        String expected = "Error: Unable to access URL";

        assertThat(ErrorSimilarityDetector.normalizeErrorMessage(message)).isEqualTo(expected);
    }

    @Test
    void testNormalizeErrorMessage_withPath() {
        String message = "Error: File not found at /path/to/file";
        String expected = "Error: File not found at PATH";

        assertThat(ErrorSimilarityDetector.normalizeErrorMessage(message)).isEqualTo(expected);
    }

    @Test
    void testNormalizeErrorMessage_withTimestamp() {
        String message = "Error: Event occurred at 2023-10-01T12:34:56";
        String expected = "Error: Event occurred at TIMESTAMP";

        assertThat(ErrorSimilarityDetector.normalizeErrorMessage(message)).isEqualTo(expected);
    }
}
