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
 * Generates valid longitude coordinates (-180 to 180).
 */
@Singleton
public class LongitudeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "longitude".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "lon".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "lng".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("longitude") ||
                PropertySanitizer.sanitize(propertyName).endsWith("lon") ||
                PropertySanitizer.sanitize(propertyName).endsWith("lng");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("longitude", "lon", "lng");
    }

    @Override
    public Object generate(Schema<?> schema) {
        double longitude = (CatsRandom.instance().nextDouble() * 360.0) - 180.0;
        String generated = String.format(Locale.US, "%.6f", longitude);
        
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "180.000001";
    }

    @Override
    public String getTotallyWrongValue() {
        return "400.0";
    }
}
