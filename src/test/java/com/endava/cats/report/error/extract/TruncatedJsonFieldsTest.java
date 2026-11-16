package com.endava.cats.report.error.extract;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TruncatedJsonFieldsTest {
    @Test
    void shouldKeepFirstDuplicateAndDocumentOrderWhileIgnoringIncompleteValues() {
        String body = "{\"message\":\"first\\nline\",\"nested\":{\"message\":\"second\",\"code\":\"E1\"},\"detail\":\"cut";

        assertThat(TruncatedJsonFields.recover(body)).containsExactly(
                Map.entry("message", "first\nline"), Map.entry("code", "E1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"trace", "stackTrace", "stack", "exceptionTrace", "stack_trace", "backtrace"})
    void shouldRecoverUnterminatedStackTraces(String key) {
        assertThat(TruncatedJsonFields.trace("{\"" + key + "\":\"failure\\n at frame"))
                .isEqualTo("failure\n at frame");
    }

    @Test
    void shouldReturnNoFieldsOrTraceWhenNoneSurvived() {
        assertThat(TruncatedJsonFields.recover("{\"message\":\"cut")).isEmpty();
        assertThat(TruncatedJsonFields.trace("{\"message\":\"bad\"")).isEmpty();
    }

    @Test
    void shouldPreserveMalformedUnicodeEscapesRatherThanLoseTheMessage() {
        String malformed = "bad " + '\\' + "u12xz";

        assertThat(TruncatedJsonFields.unescape(malformed)).isEqualTo(malformed);
        assertThat(TruncatedJsonFields.unescape("a\\n\\\"b\\\"\\t\\u0041")).isEqualTo("a\n\"b\"\tA");
    }
}
