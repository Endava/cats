package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;

public class PrefixFuzzingStrategy extends FuzzingStrategy {
    @Override
    public Object process(Object value) {
        return String.valueOf(data) + String.valueOf(value);
    }

    @Override
    public String name() {
        return "PREFIX";
    }
}
