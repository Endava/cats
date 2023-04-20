package com.endava.cats.strategy;

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
