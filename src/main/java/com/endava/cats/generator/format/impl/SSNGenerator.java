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

import java.util.ArrayList;
import java.util.List;

/**
 * Generates valid Social Security Numbers for US and European countries.
 * Supports: US SSN, UK NI, Dutch BSN, German SV, French INSEE, Swedish personnummer.
 */
@Singleton
public class SSNGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitized = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);

        return "ssn".equalsIgnoreCase(sanitized) ||
                "nin".equalsIgnoreCase(sanitized) ||
                "bsn".equalsIgnoreCase(sanitized) ||
                "personnummer".equalsIgnoreCase(sanitized) ||
                sanitizedProperty.endsWith("ssn") ||
                sanitizedProperty.endsWith("socialsecuritynumber") ||
                sanitizedProperty.endsWith("socialsecurity") ||
                sanitizedProperty.endsWith("nationalinsurancenumber") ||
                sanitizedProperty.endsWith("nin") ||
                sanitizedProperty.endsWith("bsn") ||
                sanitizedProperty.endsWith("personnummer") ||
                sanitizedProperty.endsWith("socialinsurancenumber");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("ssn", "social-security-number", "social_security_number", "nin", "bsn", "personnummer");
    }

    @Override
    public Object generate(Schema<?> schema) {
        List<String> candidates = new ArrayList<>();

        // US SSN: 123-45-6789
        candidates.add(String.format("%03d-%02d-%04d",
                FormatGeneratorUtil.randomInRange(1, 900),
                FormatGeneratorUtil.randomInRange(1, 100),
                FormatGeneratorUtil.randomInRange(1, 10000)));

        // UK National Insurance: AB123456C
        candidates.add(String.format("%c%c%06d%c",
                FormatGeneratorUtil.randomLetter(),
                FormatGeneratorUtil.randomLetter(),
                FormatGeneratorUtil.randomInRange(100000, 1000000),
                (char) ('A' + FormatGeneratorUtil.randomInRange(0, 4))));

        // Dutch BSN: 9 digits with checksum
        candidates.add(generateDutchBSN());

        // German Social Insurance: 12 345678 A 123
        candidates.add(String.format("%02d %06d %c %03d",
                FormatGeneratorUtil.randomNumber(2),
                FormatGeneratorUtil.randomNumber(6),
                FormatGeneratorUtil.randomLetter(),
                FormatGeneratorUtil.randomNumber(3)));

        // French INSEE: 1 85 02 75 123 456 78
        candidates.add(String.format("%d %02d %02d %02d %03d %03d %02d",
                FormatGeneratorUtil.randomInRange(1, 3),
                FormatGeneratorUtil.randomInRange(0, 100),
                FormatGeneratorUtil.randomInRange(1, 13),
                FormatGeneratorUtil.randomInRange(1, 96),
                FormatGeneratorUtil.randomNumber(3),
                FormatGeneratorUtil.randomNumber(3),
                FormatGeneratorUtil.randomNumber(2)));

        // Swedish personnummer: YYMMDD-XXXX
        candidates.add(String.format("%02d%02d%02d-%04d",
                FormatGeneratorUtil.randomInRange(0, 100),
                FormatGeneratorUtil.randomInRange(1, 13),
                FormatGeneratorUtil.randomInRange(1, 29),
                FormatGeneratorUtil.randomInRange(1000, 10000)));

        return DataFormat.matchesPatternOrNullFromList(schema, candidates);
    }

    private String generateDutchBSN() {
        // Generate 8 random digits, then calculate 9th digit using BSN algorithm
        int[] digits = new int[9];
        for (int i = 0; i < 8; i++) {
            digits[i] = FormatGeneratorUtil.randomDigit();
        }

        // Calculate checksum (11-proof)
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += digits[i] * (9 - i);
        }
        digits[8] = (sum % 11);
        if (digits[8] == 10) {
            digits[8] = 0;
        }

        StringBuilder bsn = new StringBuilder();
        for (int digit : digits) {
            bsn.append(digit);
        }
        return bsn.toString();
    }

    @Override
    public String getAlmostValidValue() {
        return CatsUtil.selectRandom(List.of(
                "000-12-3456",      // US: invalid area
                "AB000000A",        // UK: invalid number
                "000000000",        // Dutch: invalid checksum
                "00 000000 A 000"   // German: invalid format
        ));
    }

    @Override
    public String getTotallyWrongValue() {
        return CatsUtil.selectRandom(List.of(
                "123-45-678",       // US: too short
                "ABCD1234",         // UK: wrong format
                "12345",            // Too short
                "INVALID"           // Not a number
        ));
    }
}
