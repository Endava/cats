package com.endava.cats.model;

public class FuzzingResult {
    private final String json;
    private final String fuzzedValue;

    public FuzzingResult(String json, String fuzzedValue) {
        this.json = json;
        this.fuzzedValue = fuzzedValue;
    }

    public static FuzzingResult empty() {
        return new FuzzingResult("", "");
    }

    public String getJson() {
        return json;
    }

    public String getFuzzedValue() {
        return fuzzedValue;
    }
}
