package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;

@Singleton
public class TimeGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return OffsetTime.now().format(DateTimeFormatter.ISO_OFFSET_TIME);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "time".equalsIgnoreCase(format);
    }
}
