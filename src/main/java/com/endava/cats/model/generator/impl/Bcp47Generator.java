package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Random;

@Singleton
public class Bcp47Generator implements ValidDataFormatGenerator {
    private static final Random RANDOM = new Random();

    @Override
    public Object generate(Schema<?> schema) {
        String[] locales = {"en-US", "en-JP", "fr-FR", "de-DE", "de-CH", "de-JP", "ro-RO"};
        return locales[RANDOM.nextInt(locales.length)];
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "bcp47".equalsIgnoreCase(format);
    }
}
