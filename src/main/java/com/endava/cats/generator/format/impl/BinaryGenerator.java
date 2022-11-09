package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.generator.simple.StringGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Singleton
public class BinaryGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator {

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
}
