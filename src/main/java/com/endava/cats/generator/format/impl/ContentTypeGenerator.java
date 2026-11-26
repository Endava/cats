package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates content types and RFC 9110 media ranges.
 */
@Singleton
public class ContentTypeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return "contenttype".equalsIgnoreCase(sanitizedFormat) ||
                "mediarange".equalsIgnoreCase(sanitizedFormat) ||
                sanitizedProperty.endsWith("contenttype") ||
                sanitizedProperty.endsWith("mediarange");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("contentType", "content-type", "media-range");
    }

    @Override
    public Object generate(Schema<?> schema) {
        int generated = CatsRandom.instance().nextInt(StringGenerator.getUnsupportedMediaTypes().size());

        return StringGenerator.getUnsupportedMediaTypes().get(generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "application/";
    }

    @Override
    public String getTotallyWrongValue() {
        return "invalid-media-type";
    }
}