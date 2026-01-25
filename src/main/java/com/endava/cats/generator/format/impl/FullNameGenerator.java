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
 * Generator for full names.
 */
@Singleton
public class FullNameGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "fullname".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("fullname");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("fullName", "full-name", "full_name");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.catsFaker().name().fullName();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}
