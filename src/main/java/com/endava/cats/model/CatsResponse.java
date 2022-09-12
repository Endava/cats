package com.endava.cats.model;

import com.endava.cats.model.ann.Exclude;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Builder
@Getter
public class CatsResponse {
    private final int responseCode;
    private final String httpMethod;
    private final long responseTimeInMs;
    private final long numberOfWordsInResponse;
    private final long numberOfLinesInResponse;
    private final long contentLengthInBytes;
    private final JsonElement jsonBody;
    private final List<KeyValuePair<String, String>> headers;


    @Exclude
    private final String body;
    @Exclude
    private final String fuzzedField;

    public static CatsResponse from(int code, String body, String methodType, long ms) {
        return CatsResponse.builder().responseCode(code).body(body)
                .jsonBody(JsonParser.parseString(body)).httpMethod(methodType)
                .headers(Collections.emptyList()).responseTimeInMs(ms).build();
    }


    public static CatsResponse empty() {
        return CatsResponse.from(999, "{}", "", 0);
    }

    public String responseCodeAsString() {
        return String.valueOf(this.responseCode);
    }

    public String responseCodeAsResponseRange() {
        return responseCodeAsString().charAt(0) + "XX";
    }
}
