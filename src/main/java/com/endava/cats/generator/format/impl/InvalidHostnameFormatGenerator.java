package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidFormatGenerator;

public class InvalidHostnameFormatGenerator implements InvalidFormatGenerator {

    @Override
    public String getAlmostValidValue() {
        return "cool.cats.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "aaa111-aaaaa---";
    }
}
