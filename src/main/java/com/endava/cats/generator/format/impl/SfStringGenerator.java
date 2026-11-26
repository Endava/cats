package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates valid and invalid RFC 8941 Structured Fields strings ({@code sf-string}).
 */
@Singleton
public class SfStringGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "sf-string".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("sf-string");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = "\"%s\"".formatted(CatsRandom.alphanumeric(8));
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "\"unterminated";
    }

    @Override
    public String getTotallyWrongValue() {
        return "unquoted-string";
    }
}
