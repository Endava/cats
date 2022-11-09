package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class RelativeJsonPointerGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "1/id";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "relative-json-pointer".equalsIgnoreCase(format);
    }
}
