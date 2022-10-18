package com.endava.cats.strategy;

public class TrailFuzzingStrategy extends FuzzingStrategy {
    @Override
    public Object process(Object value) {
        return String.valueOf(value) + String.valueOf(data);
    }

    @Override
    public String name() {
        return "TRAIL";
    }
}
