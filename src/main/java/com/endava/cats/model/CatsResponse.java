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
    private static final int INVALID_ERROR_CODE = 999;
    private final int responseCode;
    private final String httpMethod;
    private final long responseTimeInMs;
    private final long numberOfWordsInResponse;
    private final long numberOfLinesInResponse;
    private final long contentLengthInBytes;
    private final JsonElement jsonBody;
    private final List<KeyValuePair<String, String>> headers;

    @Exclude
    private final String path;
    @Exclude
    private final String body;
    @Exclude
    private final String fuzzedField;

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

    public String responseCodeAsString() {
        return String.valueOf(this.responseCode);
    }

    public String responseCodeAsResponseRange() {
        return responseCodeAsString().charAt(0) + "XX";
    }

    public boolean containsHeader(String name) {
        return headers.stream().anyMatch(header -> WordUtils.containsAsAlphanumeric(header.getKey(), name));
    }

    public KeyValuePair<String, String> getHeader(String name) {
        return headers.stream()
                .filter(header -> WordUtils.containsAsAlphanumeric(header.getKey(), name))
                .findFirst()
                .orElse(null);
    }

    public boolean isValidErrorCode() {
        return this.responseCode != INVALID_ERROR_CODE;
    }

    public boolean exceedsExpectedResponseTime(long maxResponseTime) {
        return maxResponseTime != 0 && responseTimeInMs > maxResponseTime;
    }

    public static class CatsResponseBuilder {
        public CatsResponseBuilder withInvalidErrorCode() {
            this.responseCode = INVALID_ERROR_CODE;
            return this;
        }
    }
}
