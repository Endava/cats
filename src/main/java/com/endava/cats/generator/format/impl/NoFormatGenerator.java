package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidFormatGenerator;

public class NoFormatGenerator implements InvalidFormatGenerator {
    @Override
    public String getAlmostValidValue() {
        return null;
    }

    @Override
    public String getTotallyWrongValue() {
        return null;
    }
}
