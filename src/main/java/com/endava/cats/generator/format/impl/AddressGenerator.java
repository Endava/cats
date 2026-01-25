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
 * Generates real addresses.
 */
@Singleton
public class AddressGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return ("address".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("address"))
                && !PropertySanitizer.sanitize(propertyName).contains("email");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("address");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.catsFaker().address().fullAddress();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}