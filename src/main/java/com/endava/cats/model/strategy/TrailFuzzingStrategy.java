package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;

public class TrailFuzzingStrategy extends FuzzingStrategy {
    @Override
    public String process(String value) {
        return value + data;
    }

    @Override
    public String name() {
        return "TRAIL";
    }
}
