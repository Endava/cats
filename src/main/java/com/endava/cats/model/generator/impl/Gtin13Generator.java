package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.PropertySanitizer;
import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class Gtin13Generator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "5710798389878";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "gtin13".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "ean13".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase("europeanarticlenumber") ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase("globaltradeitemnumber") ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase("globaltradenumber");
    }
}
