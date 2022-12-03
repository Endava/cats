package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Singleton
public class DateGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public Object generate(Schema<?> schema) {
        return DATE_FORMATTER.format(LocalDateTime.now(ZoneId.of("GMT")));
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "date".equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "2021-02-30";
    }

    @Override
    public String getTotallyWrongValue() {
        return "11111-07-21";
    }
}
