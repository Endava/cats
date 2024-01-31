package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Random;

/**
 * A generator class implementing various interfaces for generating valid and invalid data formats
 * based on BCP 47 language tags. It also implements the OpenAPIFormat interface.
 */
@Singleton
public class Bcp47Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private final Random random = new Random();

    @Override
    public Object generate(Schema<?> schema) {
        String[] locales = {"en-US", "en-JP", "fr-FR", "de-DE", "de-CH", "de-JP", "ro-RO"};
        return locales[random.nextInt(locales.length)];
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "bcp47".equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "ro-US";
    }

    @Override
    public String getTotallyWrongValue() {
        return "xx-XX";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("bcp47");
    }
}
