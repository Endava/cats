package com.endava.cats.strategy;

public class NoopFuzzingStrategy extends FuzzingStrategy {
    @Override
    public Object process(Object value) {
        return value;
    }

    @Override
    public String name() {
        return "NOOP";
    }
}
