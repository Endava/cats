package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.List;

/**
 * A generator class implementing interfaces for generating valid URI (Uniform Resource Identifier) template data formats.
 * It implements the ValidDataFormatGenerator and OpenAPIFormat interfaces.
 */
@Singleton
public class URITemplateGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return "/fuzzing%s/{path}".formatted(RandomStringUtils.secure().nextAlphabetic(4));
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "uri-template".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("uri-template");
    }
}
