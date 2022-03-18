package com.endava.cats.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FuzzingResult {
    private final String json;
    private final String fuzzedValue;

    public static FuzzingResult empty() {
        return new FuzzingResult("", "");
    }
}
