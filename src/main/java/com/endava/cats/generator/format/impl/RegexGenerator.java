package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class RegexGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "[a-z0-9]+";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "regex".equalsIgnoreCase(format);
    }
}
