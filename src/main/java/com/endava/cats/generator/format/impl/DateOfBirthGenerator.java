package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Generates real world birthdays.
 */
@Singleton
public class DateOfBirthGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "dob".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).toLowerCase(Locale.ROOT).endsWith("birthday") ||
                PropertySanitizer.sanitize(propertyName).toLowerCase(Locale.ROOT).endsWith("dob") ||
                PropertySanitizer.sanitize(propertyName).toLowerCase(Locale.ROOT).endsWith("dateofbirth");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("description");
    }

    @Override
    public Object generate(Schema<?> schema) {
        Date generated = CatsUtil.faker().date().birthday();
        LocalDateTime localDateTime = generated.toInstant().atZone(ZoneId.of("GMT")).toLocalDateTime();

        return DataFormat.matchesPatternOrNull(schema, DATE_FORMATTER.format(localDateTime));
    }
}