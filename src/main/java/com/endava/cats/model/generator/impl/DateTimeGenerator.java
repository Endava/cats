package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Singleton
public class DateTimeGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return ZonedDateTime.now(ZoneId.of("GMT")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "date-time".equalsIgnoreCase(format);
    }
}
