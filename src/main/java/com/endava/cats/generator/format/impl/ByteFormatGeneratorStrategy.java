package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.FormatGeneratorStrategy;

public class ByteFormatGeneratorStrategy implements FormatGeneratorStrategy {

    @Override
    public String getAlmostValidValue() {
        return "=========================   -";
    }

    @Override
    public String getTotallyWrongValue() {
        return "$#@$#@$#@*$@#$#@";
    }
}
