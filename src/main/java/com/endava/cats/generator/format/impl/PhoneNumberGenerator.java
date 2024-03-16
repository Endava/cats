package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates real world phone numbers.
 */
@Singleton
public class PhoneNumberGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "phone".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("phone") ||
                PropertySanitizer.sanitize(propertyName).endsWith("phonenumber");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("phone", "phoneNumber", "phone-number", "phone_number");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.faker().phoneNumber().cellPhone();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}