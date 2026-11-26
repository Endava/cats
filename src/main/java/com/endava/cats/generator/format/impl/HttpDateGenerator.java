package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates valid and invalid RFC 9110 / RFC 7231 IMF-fixdate HTTP dates ({@code http-date}).
 */
@Singleton
public class HttpDateGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "httpdate".equalsIgnoreCase(PropertySanitizer.sanitize(format))
                || PropertySanitizer.sanitize(propertyName).endsWith("httpdate");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("http-date", "httpDate", "http_date");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "Thu, 32 Nov 2026 18:39:21 GMT";
    }

    @Override
    public String getTotallyWrongValue() {
        return "not-an-http-date";
    }
}
