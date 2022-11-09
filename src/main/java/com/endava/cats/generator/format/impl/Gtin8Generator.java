package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class Gtin8Generator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "40170725";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "ean8".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "gtin8".equalsIgnoreCase(PropertySanitizer.sanitize(format));
    }
}
