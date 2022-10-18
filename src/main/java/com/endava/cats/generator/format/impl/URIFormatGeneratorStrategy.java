package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.FormatGeneratorStrategy;

public class URIFormatGeneratorStrategy implements FormatGeneratorStrategy {

    @Override
    public String getAlmostValidValue() {
        return "mailto:l@s.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "\"wrongURI\"";
    }
}
