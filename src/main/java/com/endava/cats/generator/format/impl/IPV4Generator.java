package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class IPV4Generator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return "%s.%s.%s.%s".formatted(RandomStringUtils.randomNumeric(1, 255), RandomStringUtils.randomNumeric(1, 255), RandomStringUtils.randomNumeric(1, 255), RandomStringUtils.randomNumeric(1, 255));
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
    public List<String> marchingFormats() {
        return List.of("ip", "ipv4");
    }
}
