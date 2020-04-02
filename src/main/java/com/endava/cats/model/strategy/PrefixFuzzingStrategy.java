package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;

public class PrefixFuzzingStrategy extends FuzzingStrategy {
    @Override
    public String process(String value) {
        return data + value;
    }

    @Override
    public String name() {
        return "PREFIX";
    }
}
