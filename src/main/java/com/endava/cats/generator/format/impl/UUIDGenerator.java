package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.UUID;

@Singleton
public class UUIDGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator {

    @Override
    public Object generate(Schema<?> schema) {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "uuid".equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "123e4567-e89b-22d3-a456-42665544000";
    }

    @Override
    public String getTotallyWrongValue() {
        return "123e4567";
    }
}
