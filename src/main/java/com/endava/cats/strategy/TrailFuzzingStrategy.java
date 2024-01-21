package com.endava.cats.strategy;

/**
 * Fuzzing strategy that trails valid data with fuzzed values.
 */
public final class TrailFuzzingStrategy extends FuzzingStrategy {
    @Override
    public Object process(Object value) {
        return value + String.valueOf(data);
    }

    @Override
    public String name() {
        return "TRAIL";
    }
}
