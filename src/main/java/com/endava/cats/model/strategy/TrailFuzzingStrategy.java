package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;

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
