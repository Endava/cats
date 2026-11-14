package com.endava.cats.report.error.mask;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class ErrorTypeCodesTest {

    @ParameterizedTest
    @ValueSource(strings = {"USR-001", "err-01", "E_42", "auth.token.expired", "ERR:12", "E100", "E9", "v2.1", "ERR-12345", "VALIDATION_FAILED"})
    void shouldRecognizeCodes(String value) {
        Assertions.assertThat(ErrorTypeCodes.isCode(value)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ORD-83749213", "ERR-1234567", "550e8400-e29b-41d4-a716-446655440000", "abcdefabcdefabcdefabcdefabcdefab",
            "2026-11-13T10:00:00Z", "2026-11-13 10:00", "User not found", "404", "123456", "-", "ERR 12", "ERR/12"
    })
    void shouldNotKeepPerOccurrenceValuesOrFreeText(String value) {
        Assertions.assertThat(ErrorTypeCodes.isCode(value)).isFalse();
    }

    @Test
    void shouldNotKeepVeryLongTokens() {
        Assertions.assertThat(ErrorTypeCodes.isCode("z".repeat(64))).isTrue();
        Assertions.assertThat(ErrorTypeCodes.isCode("z".repeat(65))).isFalse();
    }
}
