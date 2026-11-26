package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * Generates valid and invalid RFC 8941 Structured Fields tokens ({@code sf-token}).
 */
@Singleton
public class SfTokenGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "sf-token".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("sf-token");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = "tok-%s/1".formatted(CatsRandom.alphabetic(6).toLowerCase(Locale.ROOT));
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "1invalid-leading-digit";
    }

    @Override
    public String getTotallyWrongValue() {
        return "invalid token with spaces";
    }
}
