package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class IRIGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "http://ëxample.com/cats";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "iri".equalsIgnoreCase(format);
    }
}
