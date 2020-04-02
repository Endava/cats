package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.FormatGeneratorStrategy;

public class URLFormatGeneratorStrategy implements FormatGeneratorStrategy {
    @Override
    public String getAlmostValidValue() {
        return "http://catsiscool.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "catsiscool";
    }
}
