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
 * Generates meaningful titles like book titles, article titles, etc.
 */
@Singleton
public class TitleGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return "title".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                (sanitizedProperty.endsWith("title") && !sanitizedProperty.endsWith("jobtitle"));
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("title");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.faker().book().title();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}
