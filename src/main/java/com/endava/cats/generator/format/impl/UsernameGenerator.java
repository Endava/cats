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
 * Generates readable usernames.
 */
@Singleton
public class UsernameGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "username".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "username".equalsIgnoreCase(PropertySanitizer.sanitize(propertyName));
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("username", "userName");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.faker().name().username();

        return DataFormat.generatedOrNull(schema, generated);
    }
}