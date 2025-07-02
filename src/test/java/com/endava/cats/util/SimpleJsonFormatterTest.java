package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SimpleJsonFormatterTest {

    @Test
    void shouldReturnNullWhenInputIsNull() {
        String result = SimpleJsonFormatter.formatJson(null);
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnEmptyStringWhenInputIsEmpty() {
        String result = SimpleJsonFormatter.formatJson("");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFormatValidJsonString() {
        String input = "{\"key\":\"value\",\"array\":[1,2,3]}";
        String expected = "{\n  \"key\": \"value\",\n  \"array\": [\n    1,\n    2,\n    3\n  ]\n}";
        String result = SimpleJsonFormatter.formatJson(input);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldReturnOriginalStringWhenInputIsNotJson() {
        String input = "This is not JSON";
        String result = SimpleJsonFormatter.formatJson(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    void shouldHandleEscapedQuotesInJsonString() {
        String input = """
                {"key":"value with \\"escaped quotes\\""}
                """;
        String expected = "{\n  \"key\": \"value with \\\"escaped quotes\\\"\"\n}";
        String result = SimpleJsonFormatter.formatJson(input);
        assertThat(result).isEqualTo(expected);
    }
}
