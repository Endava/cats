package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class URITemplateGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "/fuzzing/{path}";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "uri-template".equalsIgnoreCase(format);
    }
}
