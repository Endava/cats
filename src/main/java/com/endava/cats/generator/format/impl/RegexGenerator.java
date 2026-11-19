package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Generates valid and invalid regular expression patterns for {@code regex} format schemas.
 */
@Singleton
public class RegexGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final Set<String> FORMATS = Set.of("regex", "regexp");

    @Override
    public Object generate(Schema<?> schema) {
        return "[a-z0-9]+";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return FORMATS.contains(PropertySanitizer.sanitize(format));
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("regex", "regexp");
    }

    @Override
    public String getAlmostValidValue() {
        return "(?<group>[a-z0-9]+";
    }

    @Override
    public String getTotallyWrongValue() {
        return "(*VERB)[z-a]\\k<missing>{";
    }
}
