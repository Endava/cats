package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.generator.simple.StringGenerator;
import io.swagger.v3.oas.models.media.Schema;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class Gtin8Generator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return StringGenerator.generate("[0-9]+", 8, 8);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "ean8".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "gtin8".equalsIgnoreCase(PropertySanitizer.sanitize(format));
    }

    @Override
    public List<String> marchingFormats() {
        return List.of("gtin8", "ean8");
    }
}
