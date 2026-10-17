package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates quotes to fill in description fields.
 */
@Singleton
public class DescriptionGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitized = PropertySanitizer.sanitize(propertyName);
        return "description".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                sanitized.endsWith("description") || sanitized.endsWith("body");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("description");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.catsFaker().chuckNorris().fact();
        if (generated.length() < 55 && (schema.getMaxLength() == null || schema.getMaxLength() >= 120)) {
            generated = generated + " " + CatsUtil.catsFaker().chuckNorris().fact();
        }

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}