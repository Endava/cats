package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates real line2 addresses.
 */
@Singleton
public class AddressLine2Generator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "line2".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("line2");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("line2");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String finalAddress = "Floor " + CatsRandom.instance().nextInt(20) + ", Suite " + CatsRandom.instance().nextInt(10);

        return DataFormat.matchesPatternOrNull(schema, finalAddress);
    }
}
