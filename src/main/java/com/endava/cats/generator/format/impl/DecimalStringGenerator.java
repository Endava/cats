package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Generates valid and invalid decimal strings for the OpenAPI {@code decimal} and {@code decimal128} formats.
 */
@Singleton
public class DecimalStringGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final Set<String> FORMATS = Set.of("decimal", "decimal128");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return format != null && FORMATS.contains(format.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("decimal", "decimal128");
    }

    @Override
    public Object generate(Schema<?> schema) {
        int whole = FormatGeneratorUtil.randomInRange(1, 10000);
        int fraction = FormatGeneratorUtil.randomInRange(0, 100);
        return DataFormat.matchesPatternOrNull(schema, "%d.%02d".formatted(whole, fraction));
    }

    @Override
    public String getAlmostValidValue() {
        return "123.45.67";
    }

    @Override
    public String getTotallyWrongValue() {
        return "not-a-decimal";
    }
}
