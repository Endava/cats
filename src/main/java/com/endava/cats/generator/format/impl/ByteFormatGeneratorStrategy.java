package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.FormatGeneratorStrategy;

public class ByteFormatGeneratorStrategy implements FormatGeneratorStrategy {

    @Override
    public String getAlmostValidValue() {
        return "YmFzZTY0IGRlY29kZX==-";
    }

    @Override
    public String getTotallyWrongValue() {
        return "a2=========================   -";
    }
}
