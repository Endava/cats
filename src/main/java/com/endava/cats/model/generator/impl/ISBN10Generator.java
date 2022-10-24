package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.PropertySanitizer;
import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class ISBN10Generator implements ValidDataFormatGenerator {

    @Override
    public Object generate(Schema<?> schema) {
        return "0439023481";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "isbn10".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "isbn".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "isbn10".equalsIgnoreCase(PropertySanitizer.sanitize(propertyName)) ||
                "isbn".equalsIgnoreCase(PropertySanitizer.sanitize(propertyName));
    }
}
