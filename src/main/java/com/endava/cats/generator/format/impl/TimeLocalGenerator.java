package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Generates valid and invalid RFC 3339 time strings without timezone offset ({@code time-local}).
 */
@Singleton
public class TimeLocalGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final Set<String> FORMATS = Set.of("timelocal", "localtime");
    private static final Set<String> EXCLUDED_FORMATS = Set.of("datetimelocal", "localdatetime");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        if (EXCLUDED_FORMATS.contains(sanitizedFormat)
                || sanitizedProperty.endsWith("datetimelocal")
                || sanitizedProperty.endsWith("localdatetime")) {
            return false;
        }
        return FORMATS.contains(sanitizedFormat)
                || sanitizedProperty.endsWith("timelocal")
                || sanitizedProperty.endsWith("localtime");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("time-local", "local-time");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = LocalTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_LOCAL_TIME);
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "25:61:00";
    }

    @Override
    public String getTotallyWrongValue() {
        return "99:99:99";
    }
}
