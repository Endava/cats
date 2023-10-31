package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class RelativeJsonPointerGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return "1/id";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "relative-json-pointer".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("relative-json-pointer");
    }
}
