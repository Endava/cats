package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.FormatGeneratorUtil;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Generates valid and invalid US ABA Routing Transit Numbers (RTN).
 */
@Singleton
public class RoutingNumberGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final List<String> FED_PREFIXES = List.of(
            "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12",
            "21", "22", "26", "32", "61", "62", "72", "80");
    private static final int[] WEIGHTS = {3, 7, 1, 3, 7, 1, 3, 7};
    private static final Set<String> FORMATS = Set.of("aba", "routingnumber", "rtn");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return FORMATS.contains(sanitizedFormat)
                || sanitizedProperty.endsWith("routingnumber")
                || sanitizedProperty.endsWith("abaroutingnumber")
                || sanitizedProperty.endsWith("transitnumber");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("aba", "routingNumber", "routing-number", "routing_number", "rtn");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String body = CatsUtil.selectRandom(FED_PREFIXES) + FormatGeneratorUtil.randomDigits(6);
        String generated = body + computeCheckDigit(body);
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    static int computeCheckDigit(String eightDigits) {
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += WEIGHTS[i] * (eightDigits.charAt(i) - '0');
        }
        return (10 - (sum % 10)) % 10;
    }

    @Override
    public String getAlmostValidValue() {
        return "122105156";
    }

    @Override
    public String getTotallyWrongValue() {
        return "999999999";
    }
}
