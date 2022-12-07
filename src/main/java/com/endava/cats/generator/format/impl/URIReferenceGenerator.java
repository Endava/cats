package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;

import javax.inject.Singleton;
import java.util.List;

@Singleton
public class URIReferenceGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return "/fuzzing%s/".formatted(RandomStringUtils.randomAlphabetic(4));
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "uri-reference".equalsIgnoreCase(format);
    }

    @Override
    public List<String> marchingFormats() {
        return List.of("uri-reference");
    }
}
