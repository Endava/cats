package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.FormatGeneratorStrategy;

public class IPV4FormatGenerationStrategy implements FormatGeneratorStrategy {

    @Override
    public String getAlmostValidValue() {
        return "10.10.10.300";
    }

    @Override
    public String getTotallyWrongValue() {
        return "255.";
    }
}
