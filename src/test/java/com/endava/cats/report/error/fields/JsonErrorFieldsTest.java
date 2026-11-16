package com.endava.cats.report.error.fields;

import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class JsonErrorFieldsTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"type\":\"http://example.test/problem\"} | http://example.test/problem",
            "{\"type\":\"https://example.test/problem\"} | https://example.test/problem",
            "{\"type\":\"ABOUT:BLANK\"} | ABOUT:BLANK",
            "{\"error\":\"http://example.test/problem\",\"code\":\" \"} | http://example.test/problem",
            "{\"exception\":\"https://example.test/problem\",\"title\":\" Bad input \"} | Bad input",
            "{\"exception\":\"about:blank\",\"errorCode\":\" INVALID \"} | INVALID",
            "{\"exception\":\"ValidationException\",\"code\":\"OTHER\"} | ValidationException"
    })
    void shouldKeepProblemUrisUnlessAFallbackTitleIsAvailable(String json, String expected) {
        assertThat(JsonErrorFields.errorType(JsonParser.parseString(json).getAsJsonObject())).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{} | ''",
            "{\"first\":null,\"second\":\"fallback\"} | fallback",
            "{\"first\":[],\"second\":\"fallback\"} | fallback",
            "{\"first\":\" \",\"second\":\" fallback \"} | fallback",
            "{\"first\":{},\"second\":\"fallback\"} | fallback",
            "{\"first\":{\"code\":null,\"type\":\" \",\"message\":\" nested \"},\"second\":\"other\"} | nested",
            "{\"first\":42,\"second\":\"other\"} | 42",
            "{\"first\":false,\"second\":\"other\"} | false"
    })
    void shouldReadTheFirstUsablePrimitiveOrNestedMessage(String json, String expected) {
        assertThat(JsonErrorFields.firstStringProperty(JsonParser.parseString(json).getAsJsonObject(), List.of("first", "second")))
                .isEqualTo(expected);
    }
}
