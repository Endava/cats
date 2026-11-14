package com.endava.cats.report.error.extract;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

@QuarkusTest
class ErrorBodyExtractionTest {

    @Test
    void shouldExtractTomcatPagesCutInsideTheirCssByTheirTitle() {
        String cut = "<!doctype html><html lang=\"en\"><head><title>HTTP Status 400 – Bad Request</title><style type=\"text/css\">h1 {font-family:Tahoma";

        Assertions.assertThat(ErrorBodyExtraction.extract(cut)).isEqualTo(new ErrorCause("HTTP Status 400 – Bad Request", List.of(), ""));
    }

    @Test
    void shouldNotTreatXmlAsHtml() {
        Assertions.assertThat(ErrorBodyExtraction.extract("<error status=\"400\"><message>bad</message></error>").messages())
                .containsExactly("<error status=\"400\"><message>bad</message></error>");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"errors\":{\"address\":{\"city\":[\"required\"]},\"name\":[\"too long\",\"blank\"]}} | address.city: required; name: too long; name: blank",
            "{\"detail\":[{\"loc\":[\"body\",\"age\"],\"msg\":\"Field required\"}]} | body.age: Field required",
            "{\"errors\":[{\"pointer\":\"#/profile/color\",\"detail\":\"bad\"}]} | profile.color: bad",
            "[\"first\",\"second\"] | first; second"
    })
    void shouldRenderStructuredMessagesWithTheirLocation(String body, String detail) {
        Assertions.assertThat(ErrorBodyExtraction.extract(body).detail(ErrorBodyExtraction.MAX_SUMMARY_MESSAGES)).isEqualTo(detail);
    }

    @Test
    void shouldAnalyzeTheContentOfJsonStringBodies() {
        ErrorCause cause = ErrorBodyExtraction.extract("\"Cannot destructure property 'title' of 'article' as it is undefined.\"");

        Assertions.assertThat(cause).isEqualTo(new ErrorCause("", List.of("Cannot destructure property 'title' of 'article' as it is undefined."), ""));
        Assertions.assertThat(ErrorBodyExtraction.extract("\"\\nInvalid call in\\n/app/x.js:1:2\\n  104  AND: x").messages())
                .containsExactly("Invalid call in\n/app/x.js:1:2\n  104  AND: x");
        Assertions.assertThat(ErrorBodyExtraction.extract("\"a\" and \"b\"").messages()).containsExactly("\"a\" and \"b\"");
    }

    @Test
    void shouldDescribeObjectsWithoutErrorFieldsByTheirLeaves() {
        Assertions.assertThat(ErrorBodyExtraction.extract("{ \"msg\" : \"Bad credentials\", \"status\" : \"BAD_REQUEST\" }").messages())
                .containsExactly("Bad credentials");
        Assertions.assertThat(ErrorBodyExtraction.extract("{\"user\":{\"name\":\"bob\",\"tags\":[\"a\"],\"age\":null}}").messages())
                .containsExactly("user.name: bob; user.tags[]: a; user.age: null");
        Assertions.assertThat(ErrorBodyExtraction.extract("{}").messages()).containsExactly("{}");
    }

    @Test
    void shouldSplitRubyExceptionInspections() {
        ErrorCause cause = ErrorBodyExtraction.extract("{\"status\":500,\"error\":\"Internal Server Error\",\"exception\":\"#<NoMethodError: undefined method 'x' for nil>\"}");

        Assertions.assertThat(cause).isEqualTo(new ErrorCause("NoMethodError", List.of("undefined method 'x' for nil"), ""));
    }

    @Test
    void shouldAnalyzeTheHeadAndTailOfVeryLongBodies() {
        String traceback = "Traceback (most recent call last):\n" + "  File \"/usr/lib/python3.12/site-packages/x.py\", line 1, in f\n".repeat(2000)
                + "  File \"/app/app.py\", line 22, in parse_count\nValueError: bad";

        Assertions.assertThat(ErrorBodyExtraction.extract(traceback)).isEqualTo(new ErrorCause("", List.of("ValueError: bad"), "parse_count@/app/app.py:22"));
    }

    @Test
    void shouldSummarizeTheRootCause() {
        String express = "<!DOCTYPE html><html><head><title>Error</title></head><body><pre>TypeError: Cannot read properties of undefined (reading &#39;id&#39;)"
                + "<br> &nbsp; &nbsp;at ownerId (/app/app.js:26:46)</pre></body></html>";

        Assertions.assertThat(ErrorBodyExtraction.rootCauseSummary(express, "fallback"))
                .isEqualTo("Error: TypeError: Cannot read properties of undefined (reading 'id') @ ownerId@/app/app.js:26");
        Assertions.assertThat(ErrorBodyExtraction.rootCauseSummary("", "Unexpected code")).isEqualTo("Unexpected code");
        Assertions.assertThat(ErrorBodyExtraction.rootCauseSummary("", null)).isEqualTo(EmptyBody.PLACEHOLDER);
        Assertions.assertThat(ErrorBodyExtraction.rootCauseSummary("{\"title\":\"Bad Request\",\"detail\":\"bad request\"}", null)).isEqualTo("Bad Request");
    }
}
