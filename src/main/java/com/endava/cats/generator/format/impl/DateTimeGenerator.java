package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import jakarta.inject.Singleton;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Singleton
public class DateTimeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return ZonedDateTime.now(ZoneId.of("GMT")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "date-time".equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "2021-07-21-T10:22:1Z";
    }

    @Override
    public String getTotallyWrongValue() {
        return "1111-07-21T88:32:28Z";
    }

    @Override
    public List<String> marchingFormats() {
        return List.of("date-time");
    }
}
