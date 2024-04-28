package com.endava.cats.model;

import com.endava.cats.util.KeyValuePair;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Model class used to stored request details.
 */
@Getter
@Setter
@Builder
public class CatsRequest {
    List<KeyValuePair<String, Object>> headers;
    String payload;
    String httpMethod;
    String url;

    @Builder.Default
    String timestamp = DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.now());

    /**
     * Creates an empty CatsRequest with placeholder values.
     * The generated request has an empty JSON payload, an undefined HTTP method ("####"),
     * an undefined URL ("####"), and an empty list of headers.
     *
     * @return An empty CatsRequest instance.
     */
    public static CatsRequest empty() {
        CatsRequest request = CatsRequest.builder().build();
        request.payload = "{}";
        request.httpMethod = "####";
        request.url = "####";
        request.headers = Collections.emptyList();
        return request;
    }
}
