package com.endava.cats.model;

import com.endava.cats.model.ann.Exclude;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Builder
@Getter
public class CatsResponse {
    private final int responseCode;
    private final JsonElement jsonBody;
    private final String httpMethod;
    @Exclude
    private final String body;
    @Exclude
    private final String fuzzedField;
    private final long responseTimeInMs;
    @Exclude
    private final List<CatsHeader> headers;

    public static CatsResponse from(int code, String body, String methodType, long ms, List<CatsHeader> responseHeaders, Set<String> fuzzedFields) {
        return CatsResponse.builder().responseCode(code).body(body).httpMethod(methodType)
                .jsonBody(JsonParser.parseString(body)).responseTimeInMs(ms).headers(responseHeaders)
                .fuzzedField(fuzzedFields.stream().findAny().map(el -> el.substring(el.lastIndexOf("#") + 1)).orElse(null)).build();
    }

    public static CatsResponse from(int code, String body, String methodType, long ms) {
        return CatsResponse.builder().responseCode(code).body(body).httpMethod(methodType)
                .jsonBody(JsonParser.parseString(body)).headers(Collections.emptyList()).responseTimeInMs(ms).build();
    }

    public static CatsResponse empty() {
        return CatsResponse.from(99, "{}", "", 0);
    }

    public String responseCodeAsString() {
        return String.valueOf(this.responseCode);
    }

    public String responseCodeAsResponseRange() {
        return responseCodeAsString().charAt(0) + "XX";
    }
}
