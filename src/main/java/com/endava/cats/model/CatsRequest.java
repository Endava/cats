package com.endava.cats.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
public class CatsRequest {
    List<Header> headers;
    String payload;
    String httpMethod;
    String url;


    public static CatsRequest empty() {
        CatsRequest request = new CatsRequest();
        request.payload = "{}";
        request.httpMethod = "";
        request.url = "";
        return request;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setHeaders(List<Header> headers) {
        this.headers = headers;
    }

    public void setHttpMethod(String method) {
        this.httpMethod = method;
    }

    @AllArgsConstructor
    @Getter
    @ToString
    public static class Header {
        private final String name;
        private final String value;
    }
}
