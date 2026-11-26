package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Generates random time of day in HH:mm format.
 */
@Singleton
public class TimeOfDayGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    private static final Set<String> EXCLUDED_FORMATS = Set.of(
            "time", "datetime", "timelocal", "localtime", "datetimelocal", "localdatetime", "unixtime", "httpdate");
    private static final List<String> EXCLUDED_PROPERTY_SUFFIXES = List.of(
            "localtime", "timelocal", "localdatetime", "datetimelocal", "unixtime");

    @Override
    public Object generate(Schema<?> schema) {
        int hour = CatsRandom.instance().nextInt(24);
        int minute = CatsRandom.instance().nextInt(60);

        return String.format("%02d:%02d", hour, minute);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return sanitizedProperty.endsWith("time")
                && !EXCLUDED_FORMATS.contains(sanitizedFormat)
                && EXCLUDED_PROPERTY_SUFFIXES.stream().noneMatch(sanitizedProperty::endsWith);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("time");
    }
}
