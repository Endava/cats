package com.endava.cats.model;

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

    public static CatsRequest empty() {
        CatsRequest request = CatsRequest.builder().build();
        request.payload = "{}";
        request.httpMethod = "####";
        request.url = "####";
        request.headers = Collections.emptyList();
        return request;
    }

}
