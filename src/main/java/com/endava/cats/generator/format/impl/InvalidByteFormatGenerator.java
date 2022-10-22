package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidFormatGenerator;

public class InvalidByteFormatGenerator implements InvalidFormatGenerator {

    @Override
    public String getAlmostValidValue() {
        return "=========================   -";
    }

    @Override
    public String getTotallyWrongValue() {
        return "$#@$#@$#@*$@#$#@";
    }
}
