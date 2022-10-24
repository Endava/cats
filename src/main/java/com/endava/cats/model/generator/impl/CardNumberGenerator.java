package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.PropertySanitizer;
import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Locale;

@Singleton
public class CardNumberGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "4111111111111111";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "cardnumber".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).toLowerCase(Locale.ROOT).endsWith("cardnumber");
    }
}
