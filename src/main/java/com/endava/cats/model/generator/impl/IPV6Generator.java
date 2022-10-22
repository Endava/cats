package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class IPV6Generator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "21DA:D3:0:2F3B:2AA:FF:FE28:9C5A";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return propertyName.toLowerCase().endsWith("ipv6") || "ipv6".equalsIgnoreCase(format);
    }
}
