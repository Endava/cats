package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * Generates real line2 addresses.
 */
@Singleton
public class AddressLine2Generator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "line2".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).toLowerCase(Locale.ROOT).endsWith("line2");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("line2");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String finalAddress = "Floor " + CatsUtil.random().nextInt(20) + ", Suite " + CatsUtil.random().nextInt(10);

        return DataFormat.matchesPatternOrNull(schema, finalAddress);
    }
}
