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
 * A generator class implementing interfaces for generating valid ISBN-10 (International Standard Book Number) data formats.
 * It implements the ValidDataFormatGenerator, InvalidDataFormatGenerator, and OpenAPIFormat interfaces.
 */
@Singleton
public class ISBN10Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    private static final String ISBN_10 = "isbn10";
    private static final String ISBN = "isbn";

    @Override
    public Object generate(Schema<?> schema) {
        char[] digits = CatsRandom.numeric(9, 9).toCharArray();
        int checkDigit = computeCheckDigit(digits);
        if (checkDigit == 10) {
            digits[8] = digits[8] == '9' ? '8' : (char) (digits[8] + 1);
            checkDigit = computeCheckDigit(digits);
        }
        return new String(digits) + checkDigit;
    }

    static int computeCheckDigit(char[] nineDigits) {
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (10 - i) * (nineDigits[i] - '0');
        }
        return (11 - (sum % 11)) % 11;
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return ISBN_10.equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                ISBN.equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith(ISBN_10) ||
                PropertySanitizer.sanitize(propertyName).endsWith(ISBN);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of(ISBN_10, ISBN);
    }

    @Override
    public String getAlmostValidValue() {
        return "0306406153";
    }

    @Override
    public String getTotallyWrongValue() {
        return "INVALID-ISBN10";
    }
}
