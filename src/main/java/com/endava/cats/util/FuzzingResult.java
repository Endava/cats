package com.endava.cats.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FuzzingResult {
    private final String json;
    private final Object fuzzedValue;

    public static FuzzingResult empty() {
        return new FuzzingResult("", "");
    }
}
