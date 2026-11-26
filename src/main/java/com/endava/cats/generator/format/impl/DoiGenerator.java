package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * Generates valid and invalid ISO 26324 Digital Object Identifiers (DOI).
 */
@Singleton
public class DoiGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return "doi".equalsIgnoreCase(sanitizedFormat)
                || sanitizedProperty.endsWith("doi")
                || sanitizedProperty.endsWith("digitalobjectidentifier");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("doi");
    }

    @Override
    public Object generate(Schema<?> schema) {
        int registrant = FormatGeneratorUtil.randomInRange(1000, 99999);
        String suffix = CatsRandom.alphanumeric(8).toLowerCase(Locale.ROOT);
        return DataFormat.matchesPatternOrNull(schema, "10.%d/%s".formatted(registrant, suffix));
    }

    @Override
    public String getAlmostValidValue() {
        return "11.1000/182";
    }

    @Override
    public String getTotallyWrongValue() {
        return "not-a-doi";
    }
}
