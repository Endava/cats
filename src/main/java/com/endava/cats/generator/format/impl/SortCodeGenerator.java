package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates real world sort codes.
 */
@Singleton
public class SortCodeGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "sortcode".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("sortcode");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("sort-code", "sortCode");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.catsFaker().numerify("## ## ##");

        return DataFormat.matchesPatternOrNullWithCombinations(schema, generated);
    }
}