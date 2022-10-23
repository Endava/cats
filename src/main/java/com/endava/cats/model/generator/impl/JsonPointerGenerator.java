package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class JsonPointerGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "/item/0/id";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "json-pointer".equalsIgnoreCase(format);
    }
}
