package com.endava.cats.openapi.handler.api;

import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SchemaLocationTest {

    @Test
    void shouldReturnTrueForGlobalLocationWhenPathAndMethodAreNull() {
        SchemaLocation schemaLocation = new SchemaLocation(null, null, "fqn", "pointer");
        assertThat(schemaLocation.isGlobalLocation()).isTrue();
    }

    @Test
    void shouldReturnFalseForGlobalLocationWhenPathOrMethodIsNotNull() {
        SchemaLocation schemaLocationWithPath = new SchemaLocation("/path", null, "fqn", "pointer");
        SchemaLocation schemaLocationWithMethod = new SchemaLocation(null, "GET", "fqn", "pointer");

        assertThat(schemaLocationWithPath.isGlobalLocation()).isFalse();
        assertThat(schemaLocationWithMethod.isGlobalLocation()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/path, GET, /path, GET, true",
            "/path, POST, /path, GET, false",
            "/path, GET, /otherPath, GET, false",
            "/path, GET, /path, POST, false",
            "null, GET, /path, GET, false",
            "/path, null, /path, GET, false",
            "null, null, /path, GET, false",
            "null, null, null, null, false"
    }, nullValues = "null")
    void shouldMatchPathAndMethod(String schemaPath, String schemaMethod, String inputPath, String inputMethod, boolean expectedResult) {
        SchemaLocation schemaLocation = new SchemaLocation(schemaPath, schemaMethod, "fqn", "pointer");
        HttpMethod httpMethod = inputMethod != null ? HttpMethod.valueOf(inputMethod) : null;

        boolean result = schemaLocation.matchesPathAndMethod(inputPath, httpMethod);

        assertThat(result).isEqualTo(expectedResult);
    }
}
