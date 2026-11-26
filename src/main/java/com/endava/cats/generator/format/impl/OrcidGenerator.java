package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates valid and invalid ISO 27729 ORCID identifiers with ISO 7064 Mod 11-2 check digit.
 */
@Singleton
public class OrcidGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return "orcid".equalsIgnoreCase(sanitizedFormat)
                || sanitizedProperty.endsWith("orcid")
                || sanitizedProperty.endsWith("orcidid");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("orcid");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String body = "0000" + FormatGeneratorUtil.randomDigits(11);
        String full = body + computeCheckDigit(body);
        String formatted = "%s-%s-%s-%s".formatted(
                full.substring(0, 4), full.substring(4, 8), full.substring(8, 12), full.substring(12, 16));
        return DataFormat.matchesPatternOrNull(schema, formatted);
    }

    static char computeCheckDigit(String fifteenDigits) {
        int total = 0;
        for (int i = 0; i < fifteenDigits.length(); i++) {
            total = (total + (fifteenDigits.charAt(i) - '0')) * 2;
        }
        int remainder = total % 11;
        int result = (12 - remainder) % 11;
        return result == 10 ? 'X' : (char) ('0' + result);
    }

    @Override
    public String getAlmostValidValue() {
        return "0000-0002-1825-0098";
    }

    @Override
    public String getTotallyWrongValue() {
        return "INVALID_ORCID";
    }
}
