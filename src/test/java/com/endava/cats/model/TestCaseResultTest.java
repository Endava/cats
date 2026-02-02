package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Comprehensive unit tests for {@link TestCaseResult} covering all factory methods
 * and ensuring 100% code coverage.
 */
@QuarkusTest
class TestCaseResultTest {

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create TestCaseResult with all fields using builder")
        void shouldCreateTestCaseResultWithBuilder() {
            // Given
            CatsRequest request = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);
            String fullRequestPath = "http://localhost:8080/api/pets/123";
            String contractPath = "/pets/{petId}";
            String server = "http://localhost:8080";
            boolean validJson = true;
            long durationMs = 150L;

            // When
            TestCaseResult result = TestCaseResult.builder()
                    .request(request)
                    .response(response)
                    .fullRequestPath(fullRequestPath)
                    .contractPath(contractPath)
                    .server(server)
                    .validJson(validJson)
                    .durationMs(durationMs)
                    .build();

            // Then
            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result.request()).isEqualTo(request);
            Assertions.assertThat(result.response()).isEqualTo(response);
            Assertions.assertThat(result.fullRequestPath()).isEqualTo(fullRequestPath);
            Assertions.assertThat(result.contractPath()).isEqualTo(contractPath);
            Assertions.assertThat(result.server()).isEqualTo(server);
            Assertions.assertThat(result.validJson()).isTrue();
            Assertions.assertThat(result.durationMs()).isEqualTo(150L);
        }

        @Test
        @DisplayName("Should create TestCaseResult with invalid JSON flag")
        void shouldCreateTestCaseResultWithInvalidJson() {
            // Given
            CatsRequest request = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);

            // When
            TestCaseResult result = TestCaseResult.builder()
                    .request(request)
                    .response(response)
                    .fullRequestPath("http://localhost/api")
                    .contractPath("/api")
                    .server("http://localhost")
                    .validJson(false)
                    .durationMs(100L)
                    .build();

            // Then
            Assertions.assertThat(result.validJson()).isFalse();
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create TestCaseResult from successful call using fromSuccess")
        void shouldCreateFromSuccess() {
            // Given
            CatsRequest request = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);
            Mockito.when(request.getUrl()).thenReturn("http://localhost:8080/api/pets/123");
            String contractPath = "/pets/{petId}";
            String server = "http://localhost:8080";
            boolean validJson = true;
            long durationMs = 200L;

            // When
            TestCaseResult result = TestCaseResult.fromSuccess(
                    request,
                    response,
                    contractPath,
                    server,
                    validJson,
                    durationMs
            );

            // Then
            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result.request()).isEqualTo(request);
            Assertions.assertThat(result.response()).isEqualTo(response);
            Assertions.assertThat(result.fullRequestPath()).isEqualTo("http://localhost:8080/api/pets/123");
            Assertions.assertThat(result.contractPath()).isEqualTo(contractPath);
            Assertions.assertThat(result.server()).isEqualTo(server);
            Assertions.assertThat(result.validJson()).isTrue();
            Assertions.assertThat(result.durationMs()).isEqualTo(200L);
        }

        @Test
        @DisplayName("Should create TestCaseResult from exception using fromException")
        void shouldCreateFromException() {
            // Given
            CatsRequest request = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);
            Mockito.when(request.getUrl()).thenReturn("http://localhost:8080/api/error");
            String contractPath = "/api/error";
            String server = "http://localhost:8080";
            boolean validJson = false;
            long durationMs = 50L;

            // When
            TestCaseResult result = TestCaseResult.fromException(
                    request,
                    response,
                    contractPath,
                    server,
                    validJson,
                    durationMs
            );

            // Then
            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result.request()).isEqualTo(request);
            Assertions.assertThat(result.response()).isEqualTo(response);
            Assertions.assertThat(result.fullRequestPath()).isEqualTo("http://localhost:8080/api/error");
            Assertions.assertThat(result.contractPath()).isEqualTo(contractPath);
            Assertions.assertThat(result.server()).isEqualTo(server);
            Assertions.assertThat(result.validJson()).isFalse();
            Assertions.assertThat(result.durationMs()).isEqualTo(50L);
        }

        @Test
        @DisplayName("Should extract fullRequestPath from request URL in fromSuccess")
        void shouldExtractFullRequestPathFromRequestInFromSuccess() {
            // Given
            CatsRequest request = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);
            String expectedUrl = "http://api.example.com/v1/users/42?filter=active";
            Mockito.when(request.getUrl()).thenReturn(expectedUrl);

            // When
            TestCaseResult result = TestCaseResult.fromSuccess(
                    request,
                    response,
                    "/v1/users/{userId}",
                    "http://api.example.com",
                    true,
                    300L
            );

            // Then
            Assertions.assertThat(result.fullRequestPath()).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("Should extract fullRequestPath from request URL in fromException")
        void shouldExtractFullRequestPathFromRequestInFromException() {
            // Given
            CatsRequest request = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);
            String expectedUrl = "http://api.example.com/v1/timeout";
            Mockito.when(request.getUrl()).thenReturn(expectedUrl);

            // When
            TestCaseResult result = TestCaseResult.fromException(
                    request,
                    response,
                    "/v1/timeout",
                    "http://api.example.com",
                    true,
                    5000L
            );

            // Then
            Assertions.assertThat(result.fullRequestPath()).isEqualTo(expectedUrl);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle zero duration")
        void shouldHandleZeroDuration() {
            // Given
            CatsRequest request = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);
            Mockito.when(request.getUrl()).thenReturn("http://localhost/fast");

            // When
            TestCaseResult result = TestCaseResult.fromSuccess(
                    request,
                    response,
                    "/fast",
                    "http://localhost",
                    true,
                    0L
            );

            // Then
            Assertions.assertThat(result.durationMs()).isZero();
        }

        @Test
        @DisplayName("Should handle very long duration")
        void shouldHandleVeryLongDuration() {
            // Given
            CatsRequest request = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);
            Mockito.when(request.getUrl()).thenReturn("http://localhost/slow");
            long veryLongDuration = Long.MAX_VALUE;

            // When
            TestCaseResult result = TestCaseResult.fromSuccess(
                    request,
                    response,
                    "/slow",
                    "http://localhost",
                    true,
                    veryLongDuration
            );

            // Then
            Assertions.assertThat(result.durationMs()).isEqualTo(veryLongDuration);
        }

        @Test
        @DisplayName("Should handle empty strings for paths and server")
        void shouldHandleEmptyStrings() {
            // Given
            CatsRequest request = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);
            Mockito.when(request.getUrl()).thenReturn("");

            // When
            TestCaseResult result = TestCaseResult.fromSuccess(
                    request,
                    response,
                    "",
                    "",
                    true,
                    100L
            );

            // Then
            Assertions.assertThat(result.contractPath()).isEmpty();
            Assertions.assertThat(result.server()).isEmpty();
            Assertions.assertThat(result.fullRequestPath()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Record Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Should be immutable - equals and hashCode work correctly")
        void shouldBeImmutable() {
            // Given
            CatsRequest request = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);
            Mockito.when(request.getUrl()).thenReturn("http://localhost/api");

            // When
            TestCaseResult result1 = TestCaseResult.fromSuccess(
                    request, response, "/api", "http://localhost", true, 100L
            );
            TestCaseResult result2 = TestCaseResult.fromSuccess(
                    request, response, "/api", "http://localhost", true, 100L
            );

            // Then
            Assertions.assertThat(result1).isEqualTo(result2)
                    .hasSameHashCodeAs(result2);
        }

        @Test
        @DisplayName("Should have different hashCode for different values")
        void shouldHaveDifferentHashCodeForDifferentValues() {
            // Given
            CatsRequest request1 = Mockito.mock(CatsRequest.class);
            CatsRequest request2 = Mockito.mock(CatsRequest.class);
            CatsResponse response = Mockito.mock(CatsResponse.class);
            Mockito.when(request1.getUrl()).thenReturn("http://localhost/api1");
            Mockito.when(request2.getUrl()).thenReturn("http://localhost/api2");

            // When
            TestCaseResult result1 = TestCaseResult.fromSuccess(
                    request1, response, "/api1", "http://localhost", true, 100L
            );
            TestCaseResult result2 = TestCaseResult.fromSuccess(
                    request2, response, "/api2", "http://localhost", true, 100L
            );

            // Then
            Assertions.assertThat(result1).isNotEqualTo(result2);
            Assertions.assertThat(result1.hashCode()).isNotEqualTo(result2.hashCode());
        }
    }
}
