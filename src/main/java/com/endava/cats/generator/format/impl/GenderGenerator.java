package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * A generator class implementing interfaces for generating valid gender data formats.
 */
@Singleton
public class GenderGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    private static final List<String> GENDER = List.of("Male", "Female", "Other");
    private static final String GENDER_WORD = "gender";

    @Override
    public Object generate(Schema<?> schema) {
        return CatsUtil.selectRandom(GENDER);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return GENDER_WORD.equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase(GENDER_WORD);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of(GENDER_WORD);
    }
}