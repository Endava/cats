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
 * Generates real bank account numbers for multiple countries.
 * Supports: US, UK, Germany, France, Italy, Spain, Romania, Netherlands formats.
 */
@Singleton
public class BankAccountNumberGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "accountnumber".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("bankaccountnumber") ||
                PropertySanitizer.sanitize(propertyName).endsWith("bankaccountaccountnumber");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("account-number", "accountNumber");
    }

    @Override
    public Object generate(Schema<?> schema) {
        List<String> candidates = new ArrayList<>();

        // US: 12 digits (routing + account)
        candidates.add(FormatGeneratorUtil.randomDigits(9) + FormatGeneratorUtil.randomDigits(12));

        // UK: 8 digits (sort code + account number)
        candidates.add(FormatGeneratorUtil.randomDigits(6) + FormatGeneratorUtil.randomDigits(8));

        // Germany: 10 digits
        candidates.add(FormatGeneratorUtil.randomDigits(10));

        // France: 23 digits (bank code + branch + account + key)
        candidates.add(FormatGeneratorUtil.randomDigits(5) +
                FormatGeneratorUtil.randomDigits(5) +
                FormatGeneratorUtil.randomDigits(11) +
                FormatGeneratorUtil.randomDigits(2));

        // Italy: 12 digits
        candidates.add(FormatGeneratorUtil.randomDigits(12));

        // Spain: 20 digits (bank + branch + check + account)
        candidates.add(FormatGeneratorUtil.randomDigits(4) +
                FormatGeneratorUtil.randomDigits(4) +
                FormatGeneratorUtil.randomDigits(2) +
                FormatGeneratorUtil.randomDigits(10));

        // Romania: 24 characters (RO + 2 check + bank code + account)
        candidates.add("RO" + FormatGeneratorUtil.randomDigits(2) +
                FormatGeneratorUtil.randomLetters(4) +
                FormatGeneratorUtil.randomDigits(16));

        // Netherlands: 10 digits
        candidates.add(FormatGeneratorUtil.randomDigits(10));

        return DataFormat.matchesPatternOrNullFromList(schema, candidates);
    }
}