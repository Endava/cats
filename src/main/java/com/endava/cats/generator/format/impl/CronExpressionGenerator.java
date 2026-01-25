package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates valid cron expressions.
 */
@Singleton
public class CronExpressionGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    private static final List<String> COMMON_CRON_EXPRESSIONS = List.of(
            "0 0 * * *",        // Daily at midnight
            "0 */6 * * *",      // Every 6 hours
            "*/15 * * * *",     // Every 15 minutes
            "0 9 * * 1-5",      // Weekdays at 9 AM
            "0 0 1 * *",        // First day of month
            "0 12 * * 0",       // Sundays at noon
            "30 2 * * *",       // Daily at 2:30 AM
            "0 0 * * 6",        // Saturdays at midnight
            "0 */4 * * *",      // Every 4 hours
            "0 8-17 * * 1-5"    // Weekdays 8 AM to 5 PM
    );

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "cron".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "cronexpression".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("cron") ||
                PropertySanitizer.sanitize(propertyName).endsWith("cronexpression") ||
                PropertySanitizer.sanitize(propertyName).endsWith("schedule");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("cron", "cronExpression", "cron-expression", "cron_expression");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.selectRandom(COMMON_CRON_EXPRESSIONS);
        
        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}
