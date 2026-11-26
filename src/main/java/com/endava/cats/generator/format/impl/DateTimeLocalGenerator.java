package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Generates valid and invalid RFC 3339 date-time strings without timezone offset ({@code date-time-local}).
 */
@Singleton
public class DateTimeLocalGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final Set<String> FORMATS = Set.of("datetimelocal", "localdatetime");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return FORMATS.contains(sanitizedFormat)
                || sanitizedProperty.endsWith("datetimelocal")
                || sanitizedProperty.endsWith("localdatetime");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("date-time-local", "local-date-time");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "2021-07-21T10:22:01Z";
    }

    @Override
    public String getTotallyWrongValue() {
        return "1111-07-21T88:32:28";
    }
}
