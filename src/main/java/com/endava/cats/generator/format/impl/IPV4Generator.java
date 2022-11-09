package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;

@Singleton
public class IPV4Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return "10.10.10.20";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return propertyName.toLowerCase().endsWith("ip") || propertyName.toLowerCase().endsWith("ipaddress")
                || "ip".equalsIgnoreCase(format) || "ipv4".equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "10.10.10.300";
    }

    @Override
    public String getTotallyWrongValue() {
        return "255.";
    }
}
