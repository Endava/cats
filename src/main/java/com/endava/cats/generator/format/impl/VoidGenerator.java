package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

public class VoidGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> format) {
        return null;
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return true;
    }

    @Override
    public String getAlmostValidValue() {
        return null;
    }

    @Override
    public String getTotallyWrongValue() {
        return null;
    }
}
