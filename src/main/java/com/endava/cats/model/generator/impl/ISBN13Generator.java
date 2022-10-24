package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.PropertySanitizer;
import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
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
