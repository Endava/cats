package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;

/**
 * Generates valid flight codes
 */
@Singleton
public class FlightCodeGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    private static final List<String> AIRLINE_CODES = Arrays.asList(
            "AA", // American Airlines
            "DL", // Delta
            "UA", // United Airlines
            "BA", // British Airways
            "LH",  // Lufthansa
            "RO", // Tarom
            "AF" // Air France
    );

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "flightcode".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("flightcode");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("flightCode", "flight-code", "flight_code");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String airlineCode = AIRLINE_CODES.get(CatsRandom.instance().nextInt(AIRLINE_CODES.size()));
        int flightNumber = 100 + CatsRandom.instance().nextInt(900);
        String generated = airlineCode + flightNumber;

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}
