package com.endava.cats.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@Builder
public class CatsRequest {
    List<Header> headers;
    String payload;
    String httpMethod;
    String url;

    public static CatsRequest empty() {
        CatsRequest request = CatsRequest.builder().build();
        request.payload = "{}";
        request.httpMethod = "";
        request.url = "";
        return request;
    }

    @AllArgsConstructor
    @Getter
    @ToString
    public static class Header {
        private final String name;
        private final String value;
    }
}
