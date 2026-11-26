package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates valid and invalid single-character strings for the OpenAPI {@code char} format.
 */
@Singleton
public class CharGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "char".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("char");
    }

    @Override
    public Object generate(Schema<?> schema) {
        return DataFormat.matchesPatternOrNull(schema, String.valueOf(FormatGeneratorUtil.randomLetter()));
    }

    @Override
    public String getAlmostValidValue() {
        return "ab";
    }

    @Override
    public String getTotallyWrongValue() {
        return "not-a-single-char";
    }
}
