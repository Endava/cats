package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Locale;

@Singleton
public class URIGenerator implements ValidDataFormatGenerator {
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
}
