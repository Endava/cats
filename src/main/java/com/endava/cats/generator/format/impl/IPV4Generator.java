package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.util.List;

/**
 * A generator class implementing interfaces for generating valid and invalid IPv4 address data formats.
 * It also implements the OpenAPIFormat interface.
 */
@Singleton
public class IPV4Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        Random random = new Random();
        IntStream numbers = random.ints(4, 1, 254);
        List<Integer> list = numbers.boxed().collect(Collectors.toList());
        return "%s.%s.%s.%s".formatted(list.get(0).toString(), list.get(1).toString(), list.get(2).toString(), list.get(3).toString());
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

    @Override
    public List<String> matchingFormats() {
        return List.of("ip", "ipv4");
    }
}
