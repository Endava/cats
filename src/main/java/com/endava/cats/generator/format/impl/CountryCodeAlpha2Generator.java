package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

@Singleton
public class CountryCodeAlpha2Generator implements ValidDataFormatGenerator {
    private final Random random = new Random();

    @Override
    public Object generate(Schema<?> schema) {
        Set<String> isoCountries = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);
        return isoCountries.stream().skip(random.nextInt(isoCountries.size())).findFirst().orElse(Locale.ROOT.getCountry());
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "iso3166alpha2".equalsIgnoreCase(PropertySanitizer.sanitize(format));
    }
}
