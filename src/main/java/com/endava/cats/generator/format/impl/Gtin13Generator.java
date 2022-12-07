package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.generator.simple.StringGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.List;

@Singleton
public class Gtin13Generator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return StringGenerator.generate("[0-9]+", 13, 13);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "gtin13".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "ean13".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase("europeanarticlenumber") ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase("globaltradeitemnumber") ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase("globaltradenumber");
    }

    @Override
    public List<String> marchingFormats() {
        return List.of("gtin13", "ean13");
    }
}
