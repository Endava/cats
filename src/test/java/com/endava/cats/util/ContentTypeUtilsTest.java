package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

@QuarkusTest
class ContentTypeUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "application/json, application/json, true",
            "application/json;charset=UTF-8, application/json, true",
            "application/vnd.api+json, application/.*\\+?json.*, true",
            "application/hal+json, application/.*\\+?json.*, true",
            "application/xml, application/json, false",
            "application/json, [invalid, false"
    })
    void shouldMatchContentType(String actual, String configured, boolean expected) {
        Assertions.assertThat(ContentTypeUtils.matchesContentType(actual, configured)).isEqualTo(expected);
    }

    @Test
    void shouldHandleNullInMatchesContentType() {
        Assertions.assertThat(ContentTypeUtils.matchesContentType(null, "application/json")).isFalse();
        Assertions.assertThat(ContentTypeUtils.matchesContentType("application/json", null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "application/json, application/v1+json, true",
            "application/v2+json, application/v3+json, false",
            "application/v3+json, application/json, true",
            "application/vnd+json, application/json, true",
            "application/json, application/xml, false",
            "application/json; charset=utf, application/json; charset=iso, true",
            "*/*, application/json, true",
            "invalid, application/json, false"
    })
    void shouldCheckContentTypesEquivalence(String first, String second, boolean expected) {
        Assertions.assertThat(ContentTypeUtils.areContentTypesEquivalent(first, second)).isEqualTo(expected);
    }

    @Test
    void shouldResolveAcceptHeaderForStructuredAndPlainMediaTypes() {
        Assertions.assertThat(ContentTypeUtils.resolveAcceptHeader(null)).isNull();
        Assertions.assertThat(ContentTypeUtils.resolveAcceptHeader(Map.of())).isNull();
        Assertions.assertThat(ContentTypeUtils.resolveAcceptHeader(Map.of("204", List.of(), "404", List.of("application/xml"))))
                .isEqualTo("application/xml");
        Assertions.assertThat(ContentTypeUtils.resolveAcceptHeader(Map.of("200", List.of("application/json")))).isNull();
        Assertions.assertThat(ContentTypeUtils.resolveAcceptHeader(Map.of("200", List.of("*/*")))).isNull();
        Assertions.assertThat(ContentTypeUtils.resolveAcceptHeader(Map.of("200", List.of("application/hal+json"))))
                .isEqualTo("application/hal+json");
        Assertions.assertThat(ContentTypeUtils.resolveAcceptHeader(Map.of("201", List.of("application/vnd.api+json"))))
                .isEqualTo("application/vnd.api+json");
        Assertions.assertThat(ContentTypeUtils.resolveAcceptHeader(Map.of("200", List.of("application/xml"))))
                .isEqualTo("application/xml");
    }
}
