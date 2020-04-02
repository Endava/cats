package com.endava.cats.model;

import com.endava.cats.model.ann.Exclude;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CatsResponse {
    private final int responseCode;
    private final JsonElement jsonBody;
    private final String httpMethod;

    @Exclude
    private final String body;

    public static CatsResponse from(int code, String body, String methodType) {
        return CatsResponse.builder().responseCode(code).body(body).httpMethod(methodType)
                .jsonBody((new JsonParser()).parse(body)).build();
    }

    public static CatsResponse empty() {
        return CatsResponse.from(100, "{}", "SKIPPED");
    }

    public String responseCodeAsString() {
        return String.valueOf(this.responseCode);
    }
}
