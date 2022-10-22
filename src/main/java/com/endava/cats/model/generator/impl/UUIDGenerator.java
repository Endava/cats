package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.UUID;

@Singleton
public class UUIDGenerator implements ValidDataFormatGenerator {

    @Override
    public Object generate(Schema<?> schema) {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "uuid".equalsIgnoreCase(format);
    }
}
