package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

@Singleton
public class CountryCodeAlpha3Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private final Random random = new Random();

    @Override
    public Object generate(Schema<?> schema) {
        Set<String> isoCountries = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA3);
        return isoCountries.stream().skip(random.nextInt(isoCountries.size())).findFirst().orElse(Locale.ROOT.getISO3Country());
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "iso3166alpha3".equalsIgnoreCase(PropertySanitizer.sanitize(format));
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
        return List.of("iso3166alpha3", "iso3166-alpha3", "iso3166_alpha3");
    }
}
