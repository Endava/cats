package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates valid and invalid 64-character lowercase hexadecimal SHA-256 digests.
 */
@Singleton
public class Sha256Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final int SHA256_HEX_LENGTH = 64;
    private static final List<String> PROPERTY_SUFFIXES = List.of("sha256", "sha256hash", "sha256checksum", "sha256digest");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return "sha256".equalsIgnoreCase(sanitizedFormat)
                || PROPERTY_SUFFIXES.stream().anyMatch(sanitizedProperty::endsWith);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("sha256", "sha-256", "sha_256");
    }

    @Override
    public Object generate(Schema<?> schema) {
        char[] hex = new char[SHA256_HEX_LENGTH];
        for (int i = 0; i < SHA256_HEX_LENGTH; i++) {
            hex[i] = HEX_CHARS[CatsRandom.instance().nextInt(HEX_CHARS.length)];
        }
        return DataFormat.matchesPatternOrNull(schema, new String(hex));
    }

    @Override
    public String getAlmostValidValue() {
        return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85";
    }

    @Override
    public String getTotallyWrongValue() {
        return "not-a-sha256-digest";
    }
}
