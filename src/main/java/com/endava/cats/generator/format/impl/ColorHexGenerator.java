package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates hexadecimal color codes.
 */
@Singleton
public class ColorHexGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    private static final String HEX_CHARS = "0123456789ABCDEF";

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "color".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "hexcolor".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("color") ||
                PropertySanitizer.sanitize(propertyName).endsWith("hexcolor") ||
                PropertySanitizer.sanitize(propertyName).endsWith("colorcode") ||
                PropertySanitizer.sanitize(propertyName).endsWith("backgroundcolor") ||
                PropertySanitizer.sanitize(propertyName).endsWith("foregroundcolor");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("color", "hexColor", "hex-color", "hex_color");
    }

    @Override
    public Object generate(Schema<?> schema) {
        StringBuilder color = new StringBuilder("#");

        for (int i = 0; i < 6; i++) {
            color.append(HEX_CHARS.charAt(CatsRandom.instance().nextInt(16)));
        }

        String generated = color.toString();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "#GGGGGG";
    }

    @Override
    public String getTotallyWrongValue() {
        return "blue";
    }
}
