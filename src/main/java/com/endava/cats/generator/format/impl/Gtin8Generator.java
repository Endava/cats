package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * A generator class implementing interfaces for generating valid GTIN-8 (Global Trade Item Number) data formats.
 * It implements the ValidDataFormatGenerator, InvalidDataFormatGenerator, and OpenAPIFormat interfaces.
 */
@Singleton
public class Gtin8Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        String body = FormatGeneratorUtil.randomDigits(7);
        return body + computeCheckDigit(body);
    }

    static int computeCheckDigit(String sevenDigits) {
        int sum = 0;
        for (int i = 0; i < sevenDigits.length(); i++) {
            int digit = sevenDigits.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit * 3 : digit;
        }
        return (10 - (sum % 10)) % 10;
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "ean8".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "gtin8".equalsIgnoreCase(PropertySanitizer.sanitize(format));
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("gtin8", "ean8");
    }

    @Override
    public String getAlmostValidValue() {
        return "96385075";
    }

    @Override
    public String getTotallyWrongValue() {
        return "INVALID-GTIN8";
    }
}
