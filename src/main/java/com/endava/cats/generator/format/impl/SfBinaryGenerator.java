package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Generates valid and invalid RFC 8941 Structured Fields byte sequences ({@code sf-binary}).
 */
@Singleton
public class SfBinaryGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "sf-binary".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("sf-binary");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String encoded = Base64.getEncoder().encodeToString(CatsRandom.alphanumeric(8).getBytes(StandardCharsets.UTF_8));
        return DataFormat.matchesPatternOrNull(schema, ":%s:".formatted(encoded));
    }

    @Override
    public String getAlmostValidValue() {
        return ":cHJldGVuZA==";
    }

    @Override
    public String getTotallyWrongValue() {
        return ":not-valid-base64$$$:";
    }
}
