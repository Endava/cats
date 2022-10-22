package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidFormatGenerator;

public class InvalidPasswordFormatGenerator implements InvalidFormatGenerator {

    @Override
    public String getAlmostValidValue() {
        return "bgZD89DEkl";
    }

    @Override
    public String getTotallyWrongValue() {
        return "abcdefgh";
    }
}
