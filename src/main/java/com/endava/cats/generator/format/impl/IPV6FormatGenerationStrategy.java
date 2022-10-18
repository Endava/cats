package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.FormatGeneratorStrategy;

public class IPV6FormatGenerationStrategy implements FormatGeneratorStrategy {

    @Override
    public String getAlmostValidValue() {
        return "2001:db8:85a3:8d3:1319:8a2e:370:99999";
    }

    @Override
    public String getTotallyWrongValue() {
        return "2001:db8:85a3";
    }
}
