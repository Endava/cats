package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.PropertySanitizer;
import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Currency;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

@Singleton
public class CurrencyCodeGenerator implements ValidDataFormatGenerator {
    private final Random random = new Random();

    @Override
    public Object generate(Schema<?> schema) {
        Set<Currency> currencySet = Currency.getAvailableCurrencies();
        return currencySet.stream().skip(random.nextInt(currencySet.size())).findFirst().orElse(Currency.getInstance(Locale.UK)).getCurrencyCode();
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "currencycode".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "iso-4217".equalsIgnoreCase(format) ||
                PropertySanitizer.sanitize(propertyName).toLowerCase(Locale.ROOT).endsWith("currencycode");
    }
}
