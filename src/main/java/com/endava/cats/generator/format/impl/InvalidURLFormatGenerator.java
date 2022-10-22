package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidFormatGenerator;

public class InvalidURLFormatGenerator implements InvalidFormatGenerator {
    @Override
    public String getAlmostValidValue() {
        return "http://catsiscool.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "catsiscool";
    }
}
