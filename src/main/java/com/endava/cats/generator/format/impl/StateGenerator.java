package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * Generates valid states.
 */
@Singleton
public class StateGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "state".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).toLowerCase(Locale.ROOT).endsWith("state") ||
                PropertySanitizer.sanitize(propertyName).toLowerCase(Locale.ROOT).endsWith("statename");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("state", "stateName", "state-name", "state_name");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.faker().address().state();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}