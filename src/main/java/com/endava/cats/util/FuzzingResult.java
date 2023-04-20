package com.endava.cats.util;

public record FuzzingResult(String json, Object fuzzedValue) {
    public static FuzzingResult empty() {
        return new FuzzingResult("", "");
    }
}
