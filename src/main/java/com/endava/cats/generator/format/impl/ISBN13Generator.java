package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class ISBN13Generator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "9780439023481";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "isbn13".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "isbn13".equalsIgnoreCase(PropertySanitizer.sanitize(propertyName));
    }
}
