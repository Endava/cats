package com.endava.cats.strategy;

import com.endava.cats.util.CatsUtil;

/**
 * Fuzzing strategy that inserts fuzzed values into valid data.
 */
public final class InsertFuzzingStrategy extends FuzzingStrategy {
    @Override
    public Object process(Object value) {
        return CatsUtil.insertInTheMiddle(String.valueOf(value), String.valueOf(data), true);
    }

    @Override
    public String name() {
        return "INSERT";
    }
}
