package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class IPV6Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return "21DA:D3:0:2F3B:2AA:FF:FE28:9C5A";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return propertyName.toLowerCase().endsWith("ipv6") || "ipv6".equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "2001:db8:85a3:8d3:1319:8a2e:370:99999";
    }

    @Override
    public String getTotallyWrongValue() {
        return "2001:db8:85a3";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("ipv6");
    }
}
