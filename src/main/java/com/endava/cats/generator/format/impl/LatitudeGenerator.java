package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * Generates valid latitude coordinates (-90 to 90).
 */
@Singleton
public class LatitudeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "latitude".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "lat".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("latitude") ||
                PropertySanitizer.sanitize(propertyName).endsWith("lat");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("latitude", "lat");
    }

    @Override
    public Object generate(Schema<?> schema) {
        double latitude = (CatsRandom.instance().nextDouble() * 180.0) - 90.0;
        String generated = String.format(Locale.US, "%.6f", latitude);
        
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "90.000001";
    }

    @Override
    public String getTotallyWrongValue() {
        return "200.0";
    }
}
