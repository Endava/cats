package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidFormatGenerator;

public class InvalidEmailFormatGenerator implements InvalidFormatGenerator {

    @Override
    public String getAlmostValidValue() {
        return "email@bubu.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "bubulina";
    }
}
