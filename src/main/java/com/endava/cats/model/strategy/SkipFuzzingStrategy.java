package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;

public class SkipFuzzingStrategy extends FuzzingStrategy {
    @Override
    public String process(String value) {
        return data;
    }

    @Override
    public String name() {
        return "SKIP";
    }
}
