package com.endava.cats.strategy;

public final class ReplaceFuzzingStrategy extends FuzzingStrategy {

    @Override
    public Object process(Object value) {
        return data;
    }

    @Override
    public String name() {
        return "REPLACE";
    }
}
