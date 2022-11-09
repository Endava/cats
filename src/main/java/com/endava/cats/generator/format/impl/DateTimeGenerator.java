package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Singleton
public class DateTimeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator {
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
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:'Z'").format(LocalDateTime.now(ZoneId.systemDefault()));
    }

    @Override
    public String getTotallyWrongValue() {
        return "1000-07-21T88:32:28Z";
    }
}
