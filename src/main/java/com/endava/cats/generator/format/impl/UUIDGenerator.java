package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

/**
 * A generator class implementing interfaces for generating valid and invalid UUID (Universally Unique Identifier) data formats.
 * It also implements the OpenAPIFormat interface.
 */
@Singleton
public class UUIDGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

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
        return "123e4567-e89b-22d3-a456-426655440-92";
    }

    @Override
    public String getTotallyWrongValue() {
        return "123e4567";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("uuid");
    }
}
