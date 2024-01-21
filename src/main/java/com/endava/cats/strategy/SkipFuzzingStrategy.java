package com.endava.cats.strategy;

/**
 * Fuzzing strategy marking the actual fuzzing was not performed, but rather skipped.
 */
public final class SkipFuzzingStrategy extends FuzzingStrategy {
    @Override
    public Object process(Object value) {
        return data;
    }

    @Override
    public String name() {
        return "SKIP";
    }
}
