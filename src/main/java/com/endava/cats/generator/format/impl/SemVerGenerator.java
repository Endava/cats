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
 * Generates semantic version numbers (SemVer).
 */
@Singleton
public class SemVerGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "semver".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "version".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("version") ||
                PropertySanitizer.sanitize(propertyName).endsWith("semver") ||
                PropertySanitizer.sanitize(propertyName).endsWith("appversion") ||
                PropertySanitizer.sanitize(propertyName).endsWith("apiversion");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("semver", "version", "semantic-version", "semantic_version");
    }

    @Override
    public Object generate(Schema<?> schema) {
        int major = CatsRandom.instance().nextInt(10);
        int minor = CatsRandom.instance().nextInt(20);
        int patch = CatsRandom.instance().nextInt(50);

        String generated = String.format("%d.%d.%d", major, minor, patch);

        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "1.2";
    }

    @Override
    public String getTotallyWrongValue() {
        return "v1.2.3.4.5";
    }
}
