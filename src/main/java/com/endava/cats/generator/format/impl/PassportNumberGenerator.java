package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates realistic passport numbers for US and European countries.
 * Supports: US, UK, German, French, Italian, Spanish, Dutch formats.
 */
@Singleton
public class PassportNumberGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "passport".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "passportnumber".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("passport") ||
                PropertySanitizer.sanitize(propertyName).endsWith("passportnumber") ||
                PropertySanitizer.sanitize(propertyName).endsWith("passportno");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("passport", "passportNumber", "passport-number", "passport_number");
    }

    @Override
    public Object generate(Schema<?> schema) {
        List<String> candidates = new ArrayList<>();

        // US, UK, RO Passport: 9 digits
        candidates.add(FormatGeneratorUtil.randomDigits(9));

        // US Passport (newer), Italian, Dutch: 2 letters + 7 digits
        candidates.add(FormatGeneratorUtil.randomLetters(2) + FormatGeneratorUtil.randomDigits(7));

        // German Passport: C + 8 alphanumeric
        candidates.add("C" + FormatGeneratorUtil.randomAlphanumeric(8));

        // French Passport: 2 digits + 2 letters + 5 digits
        candidates.add(String.format("%02d%c%c%05d",
                FormatGeneratorUtil.randomNumber(2),
                FormatGeneratorUtil.randomLetter(),
                FormatGeneratorUtil.randomLetter(),
                FormatGeneratorUtil.randomNumber(5)));

        // Spanish Passport: 3 letters + 6 digits
        candidates.add(FormatGeneratorUtil.randomLetters(3) + FormatGeneratorUtil.randomDigits(6));

        return DataFormat.matchesPatternOrNullFromList(schema, candidates);
    }
}
