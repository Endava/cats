package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.List;

/**
 * A generator class implementing interfaces for generating valid International Reference Numbers (IRN) data formats.
 * It implements the ValidDataFormatGenerator and OpenAPIFormat interfaces.
 */
@Singleton
public class IRIGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        String generated = RandomStringUtils.secure().nextAlphabetic(6);
        return "http://ë%s.com/cats".formatted(generated);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "iri".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("iri");
    }
}
