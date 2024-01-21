package com.endava.cats.strategy;

/**
 * Fuzzing strategy that prefixes valid data with fuzzed values.
 */
public final class PrefixFuzzingStrategy extends FuzzingStrategy {
    @Override
    public Object process(Object value) {
        return data + String.valueOf(value);
    }

    @Override
    public String name() {
        return "PREFIX";
    }
}
