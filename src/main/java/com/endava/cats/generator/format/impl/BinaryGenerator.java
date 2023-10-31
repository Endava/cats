package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.generator.simple.StringGenerator;
import io.swagger.v3.oas.models.media.Schema;

import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Singleton
public class BinaryGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public Object generate(Schema<?> schema) {
        String value = StringGenerator.generateValueBasedOnMinMax(schema);
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "byte".equalsIgnoreCase(format) || "binary".equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "=========================   -";
    }

    @Override
    public String getTotallyWrongValue() {
        return "$#@$#@$#@*$@#$#@";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("bye", "binary");
    }
}
