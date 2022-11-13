package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;

import javax.inject.Singleton;

@Singleton
public class IRIGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        String generated = RandomStringUtils.randomAlphabetic(6);
        return "http://ë%s.com/cats".formatted(generated);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "iri".equalsIgnoreCase(format);
    }
}
