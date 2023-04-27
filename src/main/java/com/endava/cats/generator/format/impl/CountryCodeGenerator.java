package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

@Singleton
public class CountryCodeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private final Random random = new Random();

    @Override
    public Object generate(Schema<?> schema) {
        Locale.IsoCountryCode isoCountryCode = Locale.IsoCountryCode.PART1_ALPHA3;
        if (schema.getMinLength() != null && schema.getMinLength() == 2) {
            isoCountryCode = Locale.IsoCountryCode.PART1_ALPHA2;
        }
        Set<String> isoCountries = Locale.getISOCountries(isoCountryCode);
        return isoCountries.stream().skip(random.nextInt(isoCountries.size())).findFirst().orElse(Locale.UK.getCountry());
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
    public List<String> marchingFormats() {
        return List.of("iso3166", "countryCode", "country-code", "country_code");
    }
}
