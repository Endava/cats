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
 * A generator class implementing interfaces for generating valid GTIN-13 (Global Trade Item Number) data formats.
 * It implements the ValidDataFormatGenerator, InvalidDataFormatGenerator, and OpenAPIFormat interfaces.
 */
@Singleton
public class Gtin13Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        String body = FormatGeneratorUtil.randomDigits(12);
        return body + ISBN13Generator.computeCheckDigit(body);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "gtin13".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "ean13".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase("europeanarticlenumber") ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase("globaltradeitemnumber") ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase("globaltradenumber");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("gtin13", "ean13");
    }

    @Override
    public String getAlmostValidValue() {
        return "4006381333930";
    }

    @Override
    public String getTotallyWrongValue() {
        return "INVALID-GTIN13";
    }
}
