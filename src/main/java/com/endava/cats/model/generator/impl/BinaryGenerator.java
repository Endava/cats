package com.endava.cats.model.generator.impl;

import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Singleton
public class BinaryGenerator implements ValidDataFormatGenerator {

    @Override
    public Object generate(Schema<?> schema) {
        String value = StringGenerator.generateValueBasedOnMinMax(schema);
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "byte".equalsIgnoreCase(format) || "binary".equalsIgnoreCase(format);
    }
}
