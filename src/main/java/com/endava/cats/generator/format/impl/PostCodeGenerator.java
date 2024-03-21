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
 * Generates real world postal codes.
 */
@Singleton
public class PostCodeGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "zip".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("postcode") ||
                PropertySanitizer.sanitize(propertyName).endsWith("postalcode") ||
                PropertySanitizer.sanitize(propertyName).endsWith("zip") ||
                PropertySanitizer.sanitize(propertyName).endsWith("zipcode") ||
                PropertySanitizer.sanitize(propertyName).endsWith("pincode");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("postCode", "post_code", "post-code", "postalCode", "postal_code", "postal-code",
                "zip", "zipCode", "zip_code", "zip-code", "pinCode", "pin-code", "pin_code");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.faker().address().zipCode();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}