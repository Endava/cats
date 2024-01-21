package com.endava.cats.util;

/**
 * A record representing the result of a fuzzing operation, containing both the fuzzed JSON and the corresponding fuzzed value.
 *
 * @param json        The fuzzed JSON representation.
 * @param fuzzedValue The corresponding fuzzed value.
 */
public record FuzzingResult(String json, Object fuzzedValue) {

    /**
     * Creates an empty {@code FuzzingResult} with empty strings for both the fuzzed JSON and fuzzed value.
     *
     * @return An empty {@code FuzzingResult}.
     */
    public static FuzzingResult empty() {
        return new FuzzingResult("", "");
    }
}

