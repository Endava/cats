package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;

import javax.inject.Singleton;
import java.util.List;

@Singleton
public class ISBN10Generator implements ValidDataFormatGenerator, OpenAPIFormat {

    public static final String ISBN_10 = "isbn10";
    public static final String ISBN = "isbn";

    @Override
    public Object generate(Schema<?> schema) {
        return RandomStringUtils.randomNumeric(10, 10);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return ISBN_10.equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                ISBN.equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                ISBN_10.equalsIgnoreCase(PropertySanitizer.sanitize(propertyName)) ||
                ISBN.equalsIgnoreCase(PropertySanitizer.sanitize(propertyName));
    }

    @Override
    public List<String> marchingFormats() {
        return List.of(ISBN_10, ISBN);
    }
}
