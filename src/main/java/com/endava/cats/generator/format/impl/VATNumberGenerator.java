package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates VAT/GST numbers (European format).
 */
@Singleton
public class VATNumberGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    private static final List<String> COUNTRY_CODES = List.of(
            "AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", "ES", "FI",
            "FR", "GB", "GR", "HR", "HU", "IE", "IT", "LT", "LU", "LV",
            "MT", "NL", "PL", "PT", "RO", "SE", "SI", "SK"
    );

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "vat".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "vatnumber".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "gst".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("vat") ||
                PropertySanitizer.sanitize(propertyName).endsWith("vatnumber") ||
                PropertySanitizer.sanitize(propertyName).endsWith("gst") ||
                PropertySanitizer.sanitize(propertyName).endsWith("gstnumber") ||
                PropertySanitizer.sanitize(propertyName).endsWith("taxnumber");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("vat", "vatNumber", "vat-number", "vat_number", "gst", "gstNumber", "gst-number", "gst_number");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String countryCode = COUNTRY_CODES.get(CatsRandom.instance().nextInt(COUNTRY_CODES.size()));

        StringBuilder vatNumber = new StringBuilder(countryCode);

        int digits = CatsRandom.instance().nextInt(3) + 8;
        for (int i = 0; i < digits; i++) {
            vatNumber.append(CatsRandom.instance().nextInt(10));
        }

        String generated = vatNumber.toString();

        return DataFormat.matchesPatternOrNullWithCombinations(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "GB12345678";
    }

    @Override
    public String getTotallyWrongValue() {
        return "XX000000000";
    }
}
