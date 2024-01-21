package com.endava.cats.strategy;

/**
 * Fuzzing strategy that replaces valid data with fuzzed values.
 */
public final class ReplaceFuzzingStrategy extends FuzzingStrategy {

    @Override
    public Object process(Object value) {
        return data;
    }

    @Override
    public String name() {
        return "REPLACE";
    }
}
