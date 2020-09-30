package com.endava.cats.model;

import com.endava.cats.model.ann.Exclude;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class CatsResponse {
    private final int responseCode;
    private final JsonElement jsonBody;
    private final String httpMethod;
    @Exclude
    private final String body;
    private final long responseTimeInMs;
    @Exclude
    private List<CatsHeader> headers;

    public static CatsResponse from(int code, String body, String methodType, long ms, List<CatsHeader> responseHeaders) {
        return CatsResponse.builder().responseCode(code).body(body).httpMethod(methodType)
                .jsonBody(JsonParser.parseString(body)).responseTimeInMs(ms).headers(responseHeaders).build();
    }

    public static CatsResponse from(int code, String body, String methodType, long ms) {
        return CatsResponse.builder().responseCode(code).body(body).httpMethod(methodType)
                .jsonBody(JsonParser.parseString(body)).responseTimeInMs(ms).build();
    }

    public static CatsResponse empty() {
        return CatsResponse.from(100, "{}", "SKIPPED", 0);
    }

    public String responseCodeAsString() {
        return String.valueOf(this.responseCode);
    }
}
