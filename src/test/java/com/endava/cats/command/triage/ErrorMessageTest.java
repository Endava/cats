package com.endava.cats.command.triage;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class ErrorMessageTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', quoteCharacter = '`', value = {
            "XEL-4220: Operator is required for CONDITION;Operator is required for CONDITION",
            "Bad Request: name: must not be blank;name: must not be blank",
            "VALIDATION_ERROR: E1001: tax number is required;tax number is required",
            "Error: amount must not be null;amount must not be null",
            "address2: must not be blank;address2: must not be blank",
            "items[0].quantity: must not be null;items[0].quantity: must not be null",
            "plain message without qualifiers;plain message without qualifiers"})
    void shouldStripErrorCodesAndReasonPhrasesButKeepFieldPrefixes(String summary, String expected) {
        Assertions.assertThat(ErrorMessage.stripQualifiers(summary)).isEqualTo(expected);
    }

    @Test
    void shouldDetectHtmlErrorPages() {
        TriageTestCase testCase = TriageFixtures.rejected("HappyPath", "Should return [2XX]", "/orders", "{}", 400,
                "<!DOCTYPE html><html><body><p class=\"name\">Field 'name' is required</p></body></html>");

        Assertions.assertThat(ErrorMessage.of(testCase).html()).isTrue();
    }

    @Test
    void shouldTruncateLongSignatures() {
        TriageTestCase testCase = TriageFixtures.rejected("HappyPath", "Should return [2XX]", "/orders", "{}", 400, "x".repeat(150));

        Assertions.assertThat(ErrorMessage.of(testCase).signature()).hasSize(93).endsWith("...");
    }
}
