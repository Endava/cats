package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates random time of day in HH:mm format.
 */
@Singleton
public class TimeOfDayGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        int hour = CatsRandom.instance().nextInt(24);
        int minute = CatsRandom.instance().nextInt(60);

        return String.format("%02d:%02d", hour, minute);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return PropertySanitizer.sanitize(propertyName).endsWith("time") &&
                !"time".equalsIgnoreCase(format) && !"date-time".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("time");
    }
}
