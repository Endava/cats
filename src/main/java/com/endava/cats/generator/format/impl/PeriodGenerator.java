package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.time.Period;

@Singleton
public class PeriodGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return Period.of(2, 3, 4);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "period".equalsIgnoreCase(format);
    }
}
