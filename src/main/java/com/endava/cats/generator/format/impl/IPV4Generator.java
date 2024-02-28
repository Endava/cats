package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A generator class implementing interfaces for generating valid and invalid IPv4 address data formats.
 * It also implements the OpenAPIFormat interface.
 */
@Singleton
public class IPV4Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return IntStream.range(0, 4)
                .mapToObj(i -> String.valueOf(CatsUtil.random().nextInt(254) + 1))
                .collect(Collectors.joining("."));
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return (propertyName.toLowerCase().endsWith("ip") && !propertyName.toLowerCase().startsWith("zip")) ||
                propertyName.toLowerCase().endsWith("ipaddress") ||
                "ip".equalsIgnoreCase(format) ||
                "ipv4".equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "10.10.10.300";
    }

    @Override
    public String getTotallyWrongValue() {
        return "255.";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("ip", "ipv4");
    }
}
