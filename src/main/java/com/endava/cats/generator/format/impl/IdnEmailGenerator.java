package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class IdnEmailGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return RandomStringUtils.randomAlphabetic(5) + "cööl.cats@cats.io";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "idn-email".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("idn-email");
    }
}
