package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.FormatGeneratorStrategy;

public class NoFormatGenerationStrategy implements FormatGeneratorStrategy {
    @Override
    public String getAlmostValidValue() {
        return null;
    }

    @Override
    public String getTotallyWrongValue() {
        return null;
    }
}
