package com.endava.cats.report.error.mask;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class GenericMasksTest {

    @ParameterizedTest
    @MethodSource("provideNormalizeErrorMessageTestCases")
    void testNormalizeErrorMessage(String input, String expected) {
        assertThat(GenericMasks.normalizeErrorMessage(input)).isEqualTo(expected);
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
}
