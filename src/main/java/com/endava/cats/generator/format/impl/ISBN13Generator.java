package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * A generator class implementing interfaces for generating valid ISBN-13 (International Standard Book Number) data formats.
 * It implements the ValidDataFormatGenerator, InvalidDataFormatGenerator, and OpenAPIFormat interfaces.
 */
@Singleton
public class ISBN13Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final String ISBN_13 = "isbn13";

    @Override
    public Object generate(Schema<?> schema) {
        String body = "978" + CatsRandom.numeric(9, 9);
        return body + computeCheckDigit(body);
    }

    static int computeCheckDigit(String twelveDigits) {
        int sum = 0;
        for (int i = 0; i < twelveDigits.length(); i++) {
            int digit = twelveDigits.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return ISBN_13.equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                ISBN_13.equalsIgnoreCase(PropertySanitizer.sanitize(propertyName));
    }

    @Override
    public List<String> matchingFormats() {
        return List.of(ISBN_13);
    }

    @Override
    public String getAlmostValidValue() {
        return "9780306406158";
    }

    @Override
    public String getTotallyWrongValue() {
        return "INVALID-ISBN13";
    }
}
