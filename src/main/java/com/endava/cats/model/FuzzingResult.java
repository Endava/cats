package com.endava.cats.model;

import com.google.gson.JsonElement;

public class FuzzingResult {
    private final JsonElement json;
    private final String fuzzedValue;

    public FuzzingResult(JsonElement json, String fuzzedValue) {
        this.json = json;
        this.fuzzedValue = fuzzedValue;
    }

    public JsonElement getJson() {
        return json;
    }

    public String getFuzzedValue() {
        return fuzzedValue;
    }
}
