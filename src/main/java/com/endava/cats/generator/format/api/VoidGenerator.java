package com.endava.cats.generator.format.api;

import io.swagger.v3.oas.models.media.Schema;

/**
 * Void data generator.
 */
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
