package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A generator class implementing interfaces for generating valid and invalid URI (Uniform Resource Identifier) data formats.
 * It also implements the OpenAPIFormat interface.
 */
@Singleton
public class URIGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final String URL = "url";
    private static final String URI = "uri";
    private static final String LINK = "link";
    private static final Set<String> EXCLUDED_FORMATS = Set.of("base64url", "urireference", "uritemplate", "iri", "irireference");

    @Override
    public Object generate(Schema<?> schema) {
        String hero = CatsUtil.catsFaker().ancient().hero().toLowerCase(Locale.ROOT);
        String color = CatsUtil.catsFaker().color().name().toLowerCase(Locale.ROOT);
        return "https://%s-%s.com/cats".formatted(hero, color).replace(" ", "-");
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        if (URI.equalsIgnoreCase(format) || URL.equalsIgnoreCase(format)) {
            return true;
        }
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        if (EXCLUDED_FORMATS.contains(sanitizedFormat) || sanitizedProperty.endsWith("base64url")) {
            return false;
        }
        String lowerProperty = propertyName.toLowerCase(Locale.ROOT);
        return lowerProperty.endsWith(URL)
                || lowerProperty.endsWith(URI)
                || lowerProperty.endsWith(LINK);
    }

    @Override
    public String getAlmostValidValue() {
        return "http://catsiscool.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "catsiscool";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("url", "uri");
    }
}
