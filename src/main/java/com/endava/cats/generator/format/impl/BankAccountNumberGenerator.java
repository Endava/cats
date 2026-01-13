package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates real bank account numbers.
 */
@Singleton
public class BankAccountNumberGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    private static final String[] FORMATS = new String[]{"## ## ## ##", "## ## ## ## ##"};

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
        String generated = CatsUtil.faker().numerify(FORMATS[CatsRandom.instance().nextInt(FORMATS.length)]);

        return DataFormat.matchesPatternOrNullWithCombinations(schema, generated);
    }
}