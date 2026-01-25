package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates realistic license plate numbers for US and European countries.
 * Supports: US, UK, German, French, Italian, Spanish, Romanian, Dutch formats.
 */
@Singleton
public class LicensePlateGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "licenseplate".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "numberplate".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("licenseplate") ||
                PropertySanitizer.sanitize(propertyName).endsWith("numberplate") ||
                PropertySanitizer.sanitize(propertyName).endsWith("plateNumber") ||
                PropertySanitizer.sanitize(propertyName).endsWith("platenumber");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("licensePlate", "license-plate", "license_plate", "numberPlate", "number-plate", "number_plate");
    }

    @Override
    public Object generate(Schema<?> schema) {
        List<String> candidates = new ArrayList<>();

        // US formats
        candidates.add(FormatGeneratorUtil.generateFromPattern("###-AAA"));
        candidates.add(FormatGeneratorUtil.generateFromPattern("AAA-###"));
        candidates.add(FormatGeneratorUtil.generateFromPattern("AA##-AAA"));

        // UK format: AA## AAA
        candidates.add(FormatGeneratorUtil.generateFromPattern("AA## AAA"));

        // German format: AB-CD 1234
        candidates.add(FormatGeneratorUtil.generateFromPattern("AA-AA ####"));

        // French format: AB-123-CD
        candidates.add(FormatGeneratorUtil.generateFromPattern("AA-###-AA"));

        // Italian format: AB 123 CD
        candidates.add(FormatGeneratorUtil.generateFromPattern("AA ### AA"));

        // Spanish format: 1234 ABC
        candidates.add(FormatGeneratorUtil.generateFromPattern("#### AAA"));

        // Dutch format: AB-123-C
        candidates.add(FormatGeneratorUtil.generateFromPattern("AA-###-A"));

        // Romanian format: CJ 99 BUG
        candidates.add(FormatGeneratorUtil.generateFromPattern("AA ## AAA"));

        return DataFormat.matchesPatternOrNullFromList(schema, candidates);
    }
}
