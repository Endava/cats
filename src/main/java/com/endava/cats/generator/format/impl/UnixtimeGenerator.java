package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

/**
 * A generator class implementing interfaces for generating valid Unix time data formats.
 * It implements the ValidDataFormatGenerator, InvalidDataFormatGenerator, and OpenAPIFormat interfaces.
 */
@Singleton
public class UnixtimeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "unixtime".equalsIgnoreCase(PropertySanitizer.sanitize(format));
    }

    @Override
    public Object generate(Schema<?> schema) {
        return Instant.now().minusSeconds(1).getEpochSecond();
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("unix-time", "unixtime", "unix_time");
    }

    @Override
    public String getAlmostValidValue() {
        return "999999999999999999999";
    }

    @Override
    public String getTotallyWrongValue() {
        return "not-a-unix-timestamp";
    }
}
