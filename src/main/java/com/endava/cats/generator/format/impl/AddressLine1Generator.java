package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import com.github.javafaker.Address;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates real line1 addresses.
 */
@Singleton
public class AddressLine1Generator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "line1".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("line1") ||
                PropertySanitizer.sanitize(propertyName).endsWith("lineone");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("line1");
    }

    @Override
    public Object generate(Schema<?> schema) {
        Address generated = CatsUtil.faker().address();
        String finalAddress = generated.streetAddress() + ", Apt. " + generated.buildingNumber();

        return DataFormat.matchesPatternOrNull(schema, finalAddress);
    }
}