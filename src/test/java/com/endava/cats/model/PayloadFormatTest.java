package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PayloadFormatTest {
    @ParameterizedTest
    @CsvSource({
            "application/json, JSON",
            "application/json; charset=utf-8, JSON",
            "application/problem+json, JSON",
            "application/vnd.api+json, JSON",
            "application/x-www-form-urlencoded, FORM",
            "text/plain, TEXT",
            "text/plain; charset=utf-8, TEXT",
            "application/x-ndjson, NDJSON",
            "application/ndjson, NDJSON",
            "application/json-seq, UNKNOWN",
            "application/xml, UNKNOWN"
    })
    void shouldClassifyMediaTypes(String contentType, PayloadFormat expected) {
        assertThat(PayloadFormat.from(contentType)).isEqualTo(expected);
    }

    @Test
    void shouldNotClassifyNdjsonAsJson() {
        assertThat(PayloadFormat.from("application/x-ndjson")).isNotEqualTo(PayloadFormat.JSON);
    }

    @Test
    void shouldReturnUnknownForMissingOrInvalidMediaType() {
        assertThat(PayloadFormat.from(null)).isEqualTo(PayloadFormat.UNKNOWN);
        assertThat(PayloadFormat.from("invalid")).isEqualTo(PayloadFormat.UNKNOWN);
    }
}
