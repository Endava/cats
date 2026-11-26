package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Generates valid and invalid RFC 4648 Section 5 Base64URL encoded strings (without padding).
 */
@Singleton
public class Base64UrlGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final int DEFAULT_RAW_LENGTH = 12;

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "base64url".equalsIgnoreCase(PropertySanitizer.sanitize(format));
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("base64url");
    }

    @Override
    public Object generate(Schema<?> schema) {
        int rawLength = computeRawLength(schema);
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(CatsRandom.alphanumeric(rawLength).getBytes(StandardCharsets.UTF_8));
        return DataFormat.matchesPatternOrNull(schema, encoded);
    }

    private static int computeRawLength(Schema<?> schema) {
        int rawLength = DEFAULT_RAW_LENGTH;
        if (schema != null && schema.getMinLength() != null && schema.getMinLength() > 0) {
            int minRaw = (int) Math.ceil((schema.getMinLength() * 3.0) / 4.0);
            rawLength = Math.max(rawLength, minRaw);
        }
        if (schema != null && schema.getMaxLength() != null && schema.getMaxLength() > 0) {
            int maxRaw = Math.max(1, (schema.getMaxLength() * 3) / 4);
            rawLength = Math.min(rawLength, maxRaw);
        }
        return rawLength;
    }

    @Override
    public String getAlmostValidValue() {
        return "abc+def/ghi=";
    }

    @Override
    public String getTotallyWrongValue() {
        return "$$$not-base64url$$$";
    }
}
