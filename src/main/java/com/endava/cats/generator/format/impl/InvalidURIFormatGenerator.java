package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidFormatGenerator;

public class InvalidURIFormatGenerator implements InvalidFormatGenerator {

    @Override
    public String getAlmostValidValue() {
        return "mailto:l@s.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "\"wrongURI\"";
    }
}
