package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.time.Instant;

@Singleton
public class UnixtimeGenerator implements ValidDataFormatGenerator {
    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "unixtime".equalsIgnoreCase(PropertySanitizer.sanitize(format));
    }

    @Override
    public Object generate(Schema<?> schema) {
        return Instant.now().minusSeconds(1).getEpochSecond();
    }
}
