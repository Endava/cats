package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Generates valid and invalid ISO 6166 International Securities Identification Numbers (ISIN).
 */
@Singleton
public class IsinGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final List<String> COUNTRY_PREFIXES = List.of("US", "GB", "DE", "FR", "NL", "CH", "JP", "CA", "IE", "LU");
    private static final Set<String> FORMATS = Set.of("isin", "iso6166");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return FORMATS.contains(sanitizedFormat)
                || sanitizedProperty.endsWith("isin")
                || sanitizedProperty.endsWith("isincode");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("isin", "iso6166");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String body = CatsUtil.selectRandom(COUNTRY_PREFIXES) + FormatGeneratorUtil.randomAlphanumeric(9);
        String generated = body + computeCheckDigit(body);
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    static int computeCheckDigit(String body) {
        String digits = expandToDigits(body);
        int sum = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if ((digits.length() - 1 - i) % 2 == 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return (10 - (sum % 10)) % 10;
    }

    private static String expandToDigits(String value) {
        StringBuilder expanded = new StringBuilder(value.length() * 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                expanded.append(c - 'A' + 10);
            } else {
                expanded.append(c);
            }
        }
        return expanded.toString();
    }

    @Override
    public String getAlmostValidValue() {
        return "US0378331006";
    }

    @Override
    public String getTotallyWrongValue() {
        return "INVALID_ISIN";
    }
}
