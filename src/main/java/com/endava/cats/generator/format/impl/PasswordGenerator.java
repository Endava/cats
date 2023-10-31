package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class PasswordGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return "catsISc00l?!useIt#";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "password".equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "bgZD89DEkl";
    }

    @Override
    public String getTotallyWrongValue() {
        return "abcdefgh";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("password");
    }
}
