package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class IPV4Generator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "10.10.10.20";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return propertyName.toLowerCase().endsWith("ip") || propertyName.toLowerCase().endsWith("ipaddress")
                || "ip".equalsIgnoreCase(format) || "ipv4".equalsIgnoreCase(format);
    }
}
