package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * A generator class implementing interfaces for generating valid data formats based on regular expressions.
 * It implements the ValidDataFormatGenerator and OpenAPIFormat interfaces.
 */
@Singleton
public class RegexGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return "[a-z0-9]+";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "regex".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("regex");
    }
}
