package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.FormatGeneratorStrategy;

public class EmailFormatGeneratorStrategy implements FormatGeneratorStrategy {

    @Override
    public String getAlmostValidValue() {
        return "email@bubu.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "bubulina";
    }
}
