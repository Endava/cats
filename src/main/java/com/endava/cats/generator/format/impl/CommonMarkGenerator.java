package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Generates CommonMark / Markdown formatted text for the OpenAPI {@code commonmark} format.
 */
@Singleton
public class CommonMarkGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    private static final Set<String> FORMATS = Set.of("commonmark", "markdown");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return FORMATS.contains(sanitizedFormat)
                || sanitizedProperty.endsWith("commonmark")
                || sanitizedProperty.endsWith("markdown");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("commonmark", "markdown");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String title = CatsUtil.catsFaker().book().title();
        String generated = "# %s\n\nSample **CommonMark** content.".formatted(title);
        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}
