package com.endava.cats.model;

import com.endava.cats.model.ann.Exclude;
import com.endava.cats.util.WordUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Model class used to hold http response details.
 */
@Builder
@Getter
public class CatsResponse {
    private static final String UNKNOWN_MEDIA_TYPE = "unknown/unknown";
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
        /**
         * Sets the response code to an invalid error code.
         *
         * @return The builder instance for method chaining.
         */
        public CatsResponseBuilder withInvalidErrorCode() {
            this.responseCode = INVALID_ERROR_CODE;
            return this;
        }
    }

    /**
     * Default content type when none is received from the service.
     *
     * @return a default content type when one is received from the server
     */
    public static String unknownContentType() {
        return UNKNOWN_MEDIA_TYPE;
    }
}
