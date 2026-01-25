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
 * Generates company registration numbers for multiple countries.
 * Supports: UK, Germany, France, Italy, Spain, Romania, Netherlands formats.
 */
@Singleton
public class CompanyRegistrationNumberGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "companynumber".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "companyregistrationnumber".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("companynumber") ||
                PropertySanitizer.sanitize(propertyName).endsWith("companyregistrationnumber") ||
                PropertySanitizer.sanitize(propertyName).endsWith("registrationnumber") ||
                PropertySanitizer.sanitize(propertyName).endsWith("companyregno");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("companyNumber", "company-number", "company_number", 
                       "companyRegistrationNumber", "company-registration-number", "company_registration_number");
    }

    @Override
    public Object generate(Schema<?> schema) {
        List<String> candidates = new ArrayList<>();

        // UK Companies House: AB123456 (2 letters + 6 digits)
        candidates.add(FormatGeneratorUtil.randomLetters(2) + FormatGeneratorUtil.randomDigits(6));

        // Germany Handelsregister: HRB 12345 (HRB/HRA + 5 digits)
        candidates.add("HRB " + FormatGeneratorUtil.randomDigits(5));
        candidates.add("HRA " + FormatGeneratorUtil.randomDigits(5));

        // France SIREN: 123456789 (9 digits)
        candidates.add(FormatGeneratorUtil.randomDigits(9));

        // Italy REA: RM-123456 (2 letters + hyphen + 6 digits)
        candidates.add(FormatGeneratorUtil.randomLetters(2) + "-" + FormatGeneratorUtil.randomDigits(6));

        // Spain CIF: A12345678 (1 letter + 8 digits)
        candidates.add(FormatGeneratorUtil.randomLetter() + FormatGeneratorUtil.randomDigits(8));

        // Romania: J40/1234/2020 (J + 2 digits + / + 4 digits + / + 4 digits)
        candidates.add(String.format("J%02d/%04d/%04d",
                FormatGeneratorUtil.randomInRange(1, 53),
                FormatGeneratorUtil.randomInRange(1, 10000),
                FormatGeneratorUtil.randomInRange(2000, 2025)));

        // Netherlands KVK: 12345678 (8 digits)
        candidates.add(FormatGeneratorUtil.randomDigits(8));

        return DataFormat.matchesPatternOrNullFromList(schema, candidates);
    }
}
