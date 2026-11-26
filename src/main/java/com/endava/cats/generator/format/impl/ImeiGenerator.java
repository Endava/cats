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
 * Generates valid and invalid 15-digit International Mobile Equipment Identity (IMEI) numbers with Luhn check digit.
 */
@Singleton
public class ImeiGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return "imei".equalsIgnoreCase(sanitizedFormat)
                || sanitizedProperty.endsWith("imei")
                || sanitizedProperty.endsWith("imeinumber");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("imei");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String body = FormatGeneratorUtil.randomDigits(14);
        String generated = body + computeLuhnCheckDigit(body);
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    static int computeLuhnCheckDigit(String fourteenDigits) {
        int sum = 0;
        for (int i = 0; i < fourteenDigits.length(); i++) {
            int digit = fourteenDigits.charAt(i) - '0';
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return (10 - (sum % 10)) % 10;
    }

    @Override
    public String getAlmostValidValue() {
        return "490154203237519";
    }

    @Override
    public String getTotallyWrongValue() {
        return "IMEI-INVALID-12";
    }
}
