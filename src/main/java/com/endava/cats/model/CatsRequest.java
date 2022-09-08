package com.endava.cats.model;

import com.endava.cats.model.KeyValuePair;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
public class CatsRequest {
    List<KeyValuePair<String, Object>> headers;
    String payload;
    String httpMethod;
    String url;

    public static CatsRequest empty() {
        CatsRequest request = CatsRequest.builder().build();
        request.payload = "{}";
        request.httpMethod = "";
        request.url = "";
        request.headers = Collections.emptyList();
        return request;
    }
}
