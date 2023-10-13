package com.endava.cats.strategy;

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
