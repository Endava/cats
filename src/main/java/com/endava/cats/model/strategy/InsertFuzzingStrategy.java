package com.endava.cats.model.strategy;

import com.endava.cats.model.CommonWithinMethods;
import com.endava.cats.model.FuzzingStrategy;

public class InsertFuzzingStrategy extends FuzzingStrategy {
    @Override
    public String process(String value) {
        return CommonWithinMethods.insertInTheMiddle(value, data, true);
    }

    @Override
    public String name() {
        return "INSERT";
    }
}
