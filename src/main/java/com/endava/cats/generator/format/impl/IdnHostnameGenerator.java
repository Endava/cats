package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.List;

/**
 * A generator class implementing interfaces for generating valid IDN (Internationalized Domain Name) hostname data formats.
 * It implements the ValidDataFormatGenerator and OpenAPIFormat interfaces.
 */
@Singleton
public class IdnHostnameGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        String generated = RandomStringUtils.randomAlphabetic(5);
        return "www.ëcats%s.com".formatted(generated);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "idn-hostname".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("idn-hostname");
    }
}
