package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates content types.
 */
@Singleton
public class ContentTypeGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "contenttype".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("contenttype");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("contentType", "content-type");
    }

    @Override
    public Object generate(Schema<?> schema) {
        int generated = CatsRandom.instance().nextInt(StringGenerator.getUnsupportedMediaTypes().size());

        return StringGenerator.getUnsupportedMediaTypes().get(generated);
    }
}