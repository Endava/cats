package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class KvPairsGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "kvpairs".equalsIgnoreCase(format);
    }

    @Override
    public Object generate(Schema<?> schema) {
        return Map.of("key", "value", "anotherKey", "anotherValue");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("kvpairs");
    }
}
