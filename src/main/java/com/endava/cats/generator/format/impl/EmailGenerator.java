package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class EmailGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    public static final String EMAIL = "email";

    @Override
    public Object generate(Schema<?> schema) {
        return RandomStringUtils.randomAlphabetic(5) + "cool.cats@cats.io";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return propertyName.toLowerCase().endsWith(EMAIL) ||
                PropertySanitizer.sanitize(propertyName).toLowerCase().endsWith("emailaddress") ||
                EMAIL.equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "email@bubu.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "bubulina";
    }

    @Override
    public List<String> marchingFormats() {
        return List.of(EMAIL);
    }
}
