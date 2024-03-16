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
 * Generates valid city names.
 */
@Singleton
public class CityGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "city".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("city") ||
                PropertySanitizer.sanitize(propertyName).endsWith("cityname");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("city", "cityName", "city-name", "city_name");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.faker().address().city();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}