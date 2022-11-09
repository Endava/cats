package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
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
