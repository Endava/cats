package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A generator class implementing various interfaces for generating valid and invalid country code data formats.
 * It also implements the OpenAPIFormat interface.
 */
@Singleton
public class CountryCodeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        String[] isoCountries = Locale.getISOCountries();
        String randomCountry = Arrays.stream(Locale.getISOCountries())
                .skip(CatsUtil.random().nextInt(isoCountries.length))
                .findFirst()
                .orElse(Locale.UK.getCountry());

        Locale locale = new Locale.Builder().setLanguage("en").setRegion(randomCountry).build();

        if (hasLengthTwo(schema) || patternMatchesTwoLetterIsoCode(schema)) {
            return locale.getCountry();
        }
        if (hasLengthThree(schema) || patternMatchesThreeLetterIsoCode(schema)) {
            return locale.getISO3Country();
        }

        return locale.getDisplayCountry();
    }

    private static boolean patternMatchesTwoLetterIsoCode(Schema<?> schema) {
        return schema.getPattern() != null && "RO".matches(schema.getPattern());
    }

    private static boolean patternMatchesThreeLetterIsoCode(Schema<?> schema) {
        return schema.getPattern() != null && "ROU".matches(schema.getPattern());
    }

    private static boolean hasLengthTwo(Schema<?> schema) {
        return (schema.getMinLength() != null && schema.getMinLength() == 2) ||
                (schema.getMaxLength() != null && schema.getMaxLength() == 2);
    }

    private static boolean hasLengthThree(Schema<?> schema) {
        return (schema.getMinLength() != null && schema.getMinLength() == 3) ||
                (schema.getMaxLength() != null && schema.getMaxLength() == 3);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String[] parts = propertyName.split("#", 0);
        String lastPart = parts[parts.length - 1];
        return "iso3166".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "countrycode".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(lastPart).toLowerCase(Locale.ROOT).endsWith("country") ||
                PropertySanitizer.sanitize(lastPart).toLowerCase(Locale.ROOT).startsWith("country");
    }

    @Override
    public String getAlmostValidValue() {
        return "ROM";
    }

    @Override
    public String getTotallyWrongValue() {
        return "XXX";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("iso3166", "countryCode", "country-code", "country_code", "country");
    }
}
