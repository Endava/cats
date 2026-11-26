package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates benign HTML-formatted text for the OpenAPI {@code html} format.
 */
@Singleton
public class HtmlGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "html".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("html");
    }

    @Override
    public Object generate(Schema<?> schema) {
        return DataFormat.matchesPatternOrNull(schema, "<p>Sample HTML content</p>");
    }
}
