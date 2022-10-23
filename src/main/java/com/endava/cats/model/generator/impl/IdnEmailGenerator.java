package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;

import javax.inject.Singleton;

@Singleton
public class IdnEmailGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return RandomStringUtils.randomAlphabetic(5) + "cööl.cats@cats.io";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "idn-email".equalsIgnoreCase(format);
    }
}
