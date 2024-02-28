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
 * A generator class implementing various interfaces for generating valid and invalid country code data formats.
 * It also implements the OpenAPIFormat interface.
 */
@Singleton
public class CountryCodeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        Locale.IsoCountryCode isoCountryCode = Locale.IsoCountryCode.PART1_ALPHA3;

        if (hasMinLengthTwo(schema) || patternMatchesTwoLetterIsoCode(schema)) {
            isoCountryCode = Locale.IsoCountryCode.PART1_ALPHA2;
        }

        Set<String> isoCountries = Locale.getISOCountries(isoCountryCode);
        return isoCountries.stream().skip(CatsUtil.random().nextInt(isoCountries.size())).findFirst().orElse(Locale.UK.getCountry());
    }

    private static boolean patternMatchesTwoLetterIsoCode(Schema<?> schema) {
        return schema.getPattern() != null && "RO".matches(schema.getPattern());
    }

    private static boolean hasMinLengthTwo(Schema<?> schema) {
        return schema.getMinLength() != null && schema.getMinLength() == 2;
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "iso3166".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "countrycode".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).toLowerCase(Locale.ROOT).endsWith("countrycode");
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
        return List.of("iso3166", "countryCode", "country-code", "country_code");
    }
}
