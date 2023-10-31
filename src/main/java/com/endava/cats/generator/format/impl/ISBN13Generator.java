package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class ISBN13Generator implements ValidDataFormatGenerator, OpenAPIFormat {

    public static final String ISBN_13 = "isbn13";

    @Override
    public Object generate(Schema<?> schema) {
        return RandomStringUtils.randomNumeric(13, 13);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return ISBN_13.equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                ISBN_13.equalsIgnoreCase(PropertySanitizer.sanitize(propertyName));
    }

    @Override
    public List<String> matchingFormats() {
        return List.of(ISBN_13);
    }
}
