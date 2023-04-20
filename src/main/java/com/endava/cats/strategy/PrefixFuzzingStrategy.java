package com.endava.cats.strategy;

public final class PrefixFuzzingStrategy extends FuzzingStrategy {
    @Override
    public Object process(Object value) {
        return String.valueOf(data) + String.valueOf(value);
    }

    @Override
    public String name() {
        return "PREFIX";
    }
}
