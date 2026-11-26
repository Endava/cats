package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * Generates valid and invalid ISO 17442 Legal Entity Identifiers (LEI).
 */
@Singleton
public class LeiGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final BigInteger MOD_97 = BigInteger.valueOf(97);
    private static final Set<String> FORMATS = Set.of("lei", "iso17442");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return FORMATS.contains(sanitizedFormat)
                || sanitizedProperty.endsWith("lei")
                || sanitizedProperty.endsWith("leicode")
                || sanitizedProperty.endsWith("legalentityidentifier");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("lei", "iso17442");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String body = FormatGeneratorUtil.randomDigits(4) + "00" + FormatGeneratorUtil.randomAlphanumeric(12);
        String generated = body + computeCheckDigits(body);
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    static String computeCheckDigits(String body) {
        StringBuilder numeric = new StringBuilder(body.length() * 2 + 2);
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (Character.isLetter(c)) {
                numeric.append(c - 'A' + 10);
            } else {
                numeric.append(c);
            }
        }
        numeric.append("00");
        int remainder = new BigInteger(numeric.toString()).mod(MOD_97).intValue();
        return "%02d".formatted(98 - remainder);
    }

    @Override
    public String getAlmostValidValue() {
        return "5493001KJTIIGC8Y1R00";
    }

    @Override
    public String getTotallyWrongValue() {
        return "NOT-A-VALID-LEI";
    }
}
