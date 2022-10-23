package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class IdnHostnameGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "www.ëndava.com";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "idn-hostname".equalsIgnoreCase(format);
    }
}
