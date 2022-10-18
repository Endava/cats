package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.FormatGeneratorStrategy;

public class UUIDFormatGeneratorStrategy implements FormatGeneratorStrategy {

    @Override
    public String getAlmostValidValue() {
        return "123e4567-e89b-22d3-a456-42665544000";
    }

    @Override
    public String getTotallyWrongValue() {
        return "123e4567";
    }
}
