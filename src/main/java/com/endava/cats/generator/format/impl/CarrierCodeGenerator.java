package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;

/**
 * A generator class implementing interfaces for generating valid carrier code data formats.
 * It implements the ValidDataFormatGenerator and OpenAPIFormat interfaces.
 */
@Singleton
public class CarrierCodeGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    private static final List<String> IATA_CODES = Arrays.asList(
            "AA", // American Airlines
            "BA", // British Airways
            "DL", // Delta Air Lines
            "UA", // United Airlines
            "AF", // Air France
            "QF", // Qantas Airways
            "LH", // Lufthansa
            "EK", // Emirates
            "SQ", // Singapore Airlines
            "JL",  // Japan Airlines
            "RO" // Tarom
    );

    private static final List<String> ICAO_CODES = Arrays.asList(
            "AAL", // American Airlines
            "BAW", // British Airways
            "DAL", // Delta Air Lines
            "UAL", // United Airlines
            "AFR", // Air France
            "QFA", // Qantas Airways
            "DLH", // Lufthansa
            "UAE", // Emirates
            "SIA", // Singapore Airlines
            "JAL",  // Japan Airlines
            "ROT" // Tarom
    );

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "iata".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "icao".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("carriercode") ||
                PropertySanitizer.sanitize(propertyName).endsWith("iatacode") ||
                PropertySanitizer.sanitize(propertyName).endsWith("icaocode");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("iata", "iata-code", "icao", "icao-code");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.selectRandom(IATA_CODES);
        if (CatsModelUtils.hasLengthThree(schema) || patternMatchesThreeLetterIcaoCode(schema)) {
            generated = CatsUtil.selectRandom(ICAO_CODES);
        }

        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    private boolean patternMatchesThreeLetterIcaoCode(Schema<?> schema) {
        return schema.getPattern() != null && "AAL".matches(schema.getPattern());
    }
}