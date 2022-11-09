package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Locale;

@Singleton
public class URIGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator {
    private static final String URL = "url";
    private static final String URI = "uri";

    @Override
    public Object generate(Schema<?> schema) {
        return "http://example.com/cats";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return URI.equals(format) || URL.equals(format) || propertyName.equalsIgnoreCase(URL)
                || propertyName.equalsIgnoreCase(URI) || propertyName.toLowerCase(Locale.ROOT).endsWith(URL)
                || propertyName.toLowerCase(Locale.ROOT).endsWith(URI);
    }

    @Override
    public String getAlmostValidValue() {
        return "http://catsiscool.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "catsiscool";
    }
}
