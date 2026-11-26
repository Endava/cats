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
 * Generates valid and invalid RFC 8941 Structured Fields booleans ({@code sf-boolean}).
 */
@Singleton
public class SfBooleanGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "sf-boolean".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("sf-boolean");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsRandom.instance().nextBoolean() ? "?1" : "?0";
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "?2";
    }

    @Override
    public String getTotallyWrongValue() {
        return "true";
    }
}
