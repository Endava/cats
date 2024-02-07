package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.time.Period;
import java.util.List;

/**
 * A generator class implementing interfaces for generating valid Period data formats.
 * It implements the ValidDataFormatGenerator and OpenAPIFormat interfaces.
 */
@Singleton
public class PeriodGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return Period.of(CatsUtil.random().nextInt(30), CatsUtil.random().nextInt(26), CatsUtil.random().nextInt(22));
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "period".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("period");
    }
}
