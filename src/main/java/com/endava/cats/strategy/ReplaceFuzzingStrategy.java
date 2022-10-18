package com.endava.cats.strategy;

public class ReplaceFuzzingStrategy extends FuzzingStrategy {

    @Override
    public Object process(Object value) {
        return data;
    }

    @Override
    public String name() {
        return "REPLACE";
    }
}
