package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A generator class implementing various interfaces for generating valid and invalid currency code data formats.
 * It also implements the OpenAPIFormat interface.
 */
@Singleton
public class CurrencyCodeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final List<String> ACTIVE_CURRENCIES = loadActiveCurrencies();

    @Override
    public Object generate(Schema<?> schema) {
        return ACTIVE_CURRENCIES.isEmpty()
                ? Currency.getInstance(Locale.UK).getCurrencyCode()
                : ACTIVE_CURRENCIES.get(CatsRandom.instance().nextInt(ACTIVE_CURRENCIES.size()));
    }

    private static List<String> loadActiveCurrencies() {
        return Stream.of(Locale.getISOCountries())
                .map(CurrencyCodeGenerator::currencyForCountry)
                .filter(Objects::nonNull)
                .filter(currency -> currency.getDefaultFractionDigits() >= 0)
                .map(Currency::getCurrencyCode)
                .distinct()
                .sorted()
                .toList();
    }

    private static Currency currencyForCountry(String country) {
        try {
            return Currency.getInstance(Locale.of("", country));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "currencycode".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "iso4217".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("currencycode") ||
                PropertySanitizer.sanitize(propertyName).endsWith("currency");
    }

    @Override
    public String getAlmostValidValue() {
        return "ROL";
    }

    @Override
    public String getTotallyWrongValue() {
        return "XXX";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("iso4217", "currencyCode", "currency-code", "currency_code");
    }
}
