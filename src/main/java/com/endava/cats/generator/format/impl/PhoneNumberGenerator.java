package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates real world phone numbers.
 */
@Singleton
public class PhoneNumberGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    private static final List<String> FORMATS = List.of("0## ### ####", "0### ### ####", "### ### ####", "#### ### ####");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "phone".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).matches(".*phone(\\d)?$") ||
                PropertySanitizer.sanitize(propertyName).matches(".*phonenumber(\\d)?$");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("phone", "phoneNumber", "phone-number", "phone_number");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String pattern;
        if (schema.getPattern() != null && (schema.getPattern().startsWith("^\\+") || schema.getPattern().startsWith("\\+"))) {
            pattern = "+40### ### ###";
        } else {
            pattern = FORMATS.get(CatsUtil.random().nextInt(FORMATS.size()));
        }
        String generated = CatsUtil.faker().numerify(pattern);

        return DataFormat.matchesPatternOrNullWithCombinations(schema, generated);
    }
}