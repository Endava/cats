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
 * Generates real world phone numbers for multiple countries.
 * Supports: US, UK, Germany, France, Italy, Spain, Romania, Netherlands formats.
 */
@Singleton
public class PhoneNumberGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

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
        List<String> candidates = new ArrayList<>();

        // US: +1 (555) 123-4567 or (555) 123-4567
        candidates.add(String.format("+1 (%03d) %03d-%04d",
                FormatGeneratorUtil.randomInRange(200, 1000),
                FormatGeneratorUtil.randomInRange(200, 1000),
                FormatGeneratorUtil.randomInRange(1000, 10000)));
        candidates.add(String.format("(%03d) %03d-%04d",
                FormatGeneratorUtil.randomInRange(200, 1000),
                FormatGeneratorUtil.randomInRange(200, 1000),
                FormatGeneratorUtil.randomInRange(1000, 10000)));

        // UK: +44 20 1234 5678 or 020 1234 5678
        candidates.add("+44 20 " + FormatGeneratorUtil.randomDigits(4) + " " + FormatGeneratorUtil.randomDigits(4));
        candidates.add("020 " + FormatGeneratorUtil.randomDigits(4) + " " + FormatGeneratorUtil.randomDigits(4));

        // Germany: +49 30 12345678 or 030 12345678
        candidates.add("+49 30 " + FormatGeneratorUtil.randomDigits(8));
        candidates.add("030 " + FormatGeneratorUtil.randomDigits(8));

        // France: +33 1 23 45 67 89 or 01 23 45 67 89
        candidates.add("+33 1 " + FormatGeneratorUtil.randomDigits(2) + " " +
                FormatGeneratorUtil.randomDigits(2) + " " +
                FormatGeneratorUtil.randomDigits(2) + " " +
                FormatGeneratorUtil.randomDigits(2));
        candidates.add("01 " + FormatGeneratorUtil.randomDigits(2) + " " +
                FormatGeneratorUtil.randomDigits(2) + " " +
                FormatGeneratorUtil.randomDigits(2) + " " +
                FormatGeneratorUtil.randomDigits(2));

        // Italy: +39 06 1234 5678 or 06 1234 5678
        candidates.add("+39 06 " + FormatGeneratorUtil.randomDigits(4) + " " + FormatGeneratorUtil.randomDigits(4));
        candidates.add("06 " + FormatGeneratorUtil.randomDigits(4) + " " + FormatGeneratorUtil.randomDigits(4));

        // Spain: +34 91 123 45 67 or 91 123 45 67
        candidates.add("+34 91 " + FormatGeneratorUtil.randomDigits(3) + " " +
                FormatGeneratorUtil.randomDigits(2) + " " +
                FormatGeneratorUtil.randomDigits(2));
        candidates.add("91 " + FormatGeneratorUtil.randomDigits(3) + " " +
                FormatGeneratorUtil.randomDigits(2) + " " +
                FormatGeneratorUtil.randomDigits(2));

        // Romania: +40 21 123 4567 or 021 123 4567
        candidates.add("+40 21 " + FormatGeneratorUtil.randomDigits(3) + " " + FormatGeneratorUtil.randomDigits(4));
        candidates.add("021 " + FormatGeneratorUtil.randomDigits(3) + " " + FormatGeneratorUtil.randomDigits(4));

        // Netherlands: +31 20 123 4567 or 020 123 4567
        candidates.add("+31 20 " + FormatGeneratorUtil.randomDigits(3) + " " + FormatGeneratorUtil.randomDigits(4));
        candidates.add("020 " + FormatGeneratorUtil.randomDigits(3) + " " + FormatGeneratorUtil.randomDigits(4));

        return DataFormat.matchesPatternOrNullFromList(schema, candidates);
    }
}