package com.endava.cats.model;

import com.endava.cats.model.ann.Exclude;
import com.endava.cats.util.WordUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Builder;
import lombok.Getter;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Model class used to hold http response details.
 */
@Builder
@Getter
public class CatsResponse {
    private static final String UNKNOWN_MEDIA_TYPE = "unknown/unknown";
    private static final Exception EMPTY_EXCEPTION = new Exception("");
    private static final int INVALID_ERROR_CODE = 999;
    private final int responseCode;
    private final String httpMethod;
    private final long responseTimeInMs;
    private final long numberOfWordsInResponse;
    private final long numberOfLinesInResponse;
    private final long contentLengthInBytes;
    private final JsonElement jsonBody;
    private final List<KeyValuePair<String, String>> headers;
    private final String responseContentType;

    @Exclude
    private final String path;
    @Exclude
    private final String body;
    @Exclude
    private final String fuzzedField;

    /**
     * Creates a CatsResponse instance with the specified parameters.
     *
     * @param code       The HTTP response code.
     * @param body       The response body.
     * @param methodType The HTTP method type.
     * @param ms         The response time in milliseconds.
     * @return A CatsResponse instance.
     */
    public static CatsResponse from(int code, String body, String methodType, long ms) {
        return CatsResponse.builder().responseCode(code).body(body)
                .jsonBody(JsonParser.parseString(body)).httpMethod(methodType)
                .headers(Collections.emptyList()).responseTimeInMs(ms).build();
    }

    /**
     * Returns an empty response with default values.
     *
     * @return an object which can be considered empty
     */
    public static CatsResponse empty() {
        return CatsResponse.from(INVALID_ERROR_CODE, "{}", "", 0);
    }

    /**
     * Retrieves the HTTP response code as a string representation.
     *
     * @return The response code as a string.
     */
    public String responseCodeAsString() {
        return String.valueOf(this.responseCode);
    }

    /**
     * Retrieves the HTTP response code as a response range string representation.
     * The response range is determined by the first digit of the response code, followed by "XX".
     *
     * @return The response code as a response range string.
     */
    public String responseCodeAsResponseRange() {
        return responseCodeAsString().charAt(0) + "XX";
    }

    /**
     * Checks if the response contains a header with the specified name.
     *
     * @param name The name of the header to check.
     * @return {@code true} if the response contains a header with the specified name, {@code false} otherwise.
     */
    public boolean containsHeader(String name) {
        return headers.stream().anyMatch(header -> WordUtils.matchesAsLowerCase(header.getKey(), name));
    }

    /**
     * Retrieves the first occurrence of a header with the specified name from the response.
     *
     * @param name The name of the header to retrieve.
     * @return A key-value pair representing the header, or {@code null} if no such header is found.
     */
    public KeyValuePair<String, String> getHeader(String name) {
        return headers.stream()
                .filter(header -> WordUtils.matchesAsLowerCase(header.getKey(), name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if the response code is a valid error code.
     *
     * @return {@code true} if the response code is not equal to the invalid error code; otherwise, {@code false}.
     */
    public boolean isValidErrorCode() {
        return this.responseCode != INVALID_ERROR_CODE;
    }

    /**
     * Checks if the response time exceeds the expected maximum response time.
     *
     * @param maxResponseTime The maximum allowed response time in milliseconds.
     * @return {@code true} if the actual response time exceeds the specified maximum response time
     * and the maximum response time is not set to zero; otherwise, {@code false}.
     */
    public boolean exceedsExpectedResponseTime(long maxResponseTime) {
        return maxResponseTime != 0 && responseTimeInMs > maxResponseTime;
    }

    /**
     * Checks if the actual content type of the response is unknown.
     * When services respond with 413, 414, 431 they might not set a content type for response.
     *
     * @return true if content type is set to {@code unknown/unknown}, false otherwise
     */
    public boolean isUnknownContentType() {
        return UNKNOWN_MEDIA_TYPE.equalsIgnoreCase(responseContentType);
    }

    /**
     * Builder for CatsResponse.
     */
    public static class CatsResponseBuilder {
    }

    /**
     * Default content type when none is received from the service.
     *
     * @return a default content type when one is received from the server
     */
    public static String unknownContentType() {
        return UNKNOWN_MEDIA_TYPE;
    }

    /**
     * Maps a given IOException to a corresponding response code and message.
     *
     * @param e the exception
     * @return an {@code ExceptionalResponse} enum with response code and message
     */
    public static ExceptionalResponse getResponseByException(Exception e) {
        if (e instanceof IOException ioException) {
            if (exceptionContains(ioException, "unexpected end of stream") ||
                    exceptionContains(ioException, "connection reset")) {
                return ExceptionalResponse.EMPTY_BODY;
            }
            if (exceptionContains(ioException, "connection refused")) {
                return ExceptionalResponse.CONNECTION_REFUSED;
            }
            if (exceptionContains(ioException, "read timeout")) {
                return ExceptionalResponse.READ_TIMEOUT;
            }
            if (exceptionContains(ioException, "write timeout")) {
                return ExceptionalResponse.WRITE_TIMEOUT;
            }
            if (exceptionContains(ioException, "connection timeout")) {
                return ExceptionalResponse.CONNECTION_TIMEOUT;
            }
            if (e instanceof ProtocolException || e.getCause() instanceof ProtocolException) {
                return ExceptionalResponse.PROTOCOL_EXCEPTION;
            }
        }
        return ExceptionalResponse.NO_BODY;
    }

    private static boolean exceptionContains(IOException ioException, String message) {
        return Optional.ofNullable(ioException.getMessage()).orElse("").toLowerCase(Locale.ROOT).contains(message) ||
                Optional.ofNullable(ioException.getCause()).orElse(EMPTY_EXCEPTION).getMessage().toLowerCase(Locale.ROOT).contains(message) ||
                Arrays.stream(ioException.getSuppressed()).anyMatch(t -> t.getMessage().toLowerCase(Locale.ROOT).contains(message));
    }

    public enum ExceptionalResponse {
        EMPTY_BODY(952, """
                {"notAJson": "empty reply from server"}
                """),
        CONNECTION_REFUSED(953, """
                {"notAJson": "connection refused! please make sure you have access to the service!"}
                """),
        READ_TIMEOUT(954, """
                {"notAJson": "read timeout! you might want to increased it using --readTimeout"}
                """),
        WRITE_TIMEOUT(955, """
                {"notAJson": "read timeout! you might want to increased it using --writeTimeout"}
                """),
        CONNECTION_TIMEOUT(956, """
                {"notAJson": "read timeout! you might want to increased it using --connectionTimeout"}
                """),
        PROTOCOL_EXCEPTION(957, """
                {"notAJson": "protocol exception! your service has issues providing a consistent response"}
                """),
        NO_BODY(INVALID_ERROR_CODE, """
                {"notAJson": "no body due to unknown error"}
                """);

        private final int responseCode;
        private final String responseBody;

        ExceptionalResponse(int responseCode, String jsonResponse) {
            this.responseCode = responseCode;
            this.responseBody = jsonResponse;
        }

        public int responseCode() {
            return responseCode;
        }

        public String responseBody() {
            return responseBody;
        }
    }
}
