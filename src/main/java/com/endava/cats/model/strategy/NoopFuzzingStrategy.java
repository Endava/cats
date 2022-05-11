package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;

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
