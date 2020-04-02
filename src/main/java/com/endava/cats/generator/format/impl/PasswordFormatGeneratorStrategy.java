package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.FormatGeneratorStrategy;

public class PasswordFormatGeneratorStrategy implements FormatGeneratorStrategy {

    @Override
    public String getAlmostValidValue() {
        return "bgZD89DEkl";
    }

    @Override
    public String getTotallyWrongValue() {
        return "abcdefgh";
    }
}
