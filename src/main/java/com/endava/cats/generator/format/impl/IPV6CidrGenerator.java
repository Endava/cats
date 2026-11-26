package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates valid and invalid RFC 4291 IPv6 CIDR network ranges ({@code ipv6-cidr}).
 */
@Singleton
public class IPV6CidrGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final List<Integer> PREFIX_LENGTHS = List.of(32, 48, 56, 64);

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return "ipv6cidr".equalsIgnoreCase(sanitizedFormat)
                || sanitizedProperty.endsWith("ipv6cidr")
                || sanitizedProperty.endsWith("ipv6range")
                || sanitizedProperty.endsWith("ipv6subnet");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("ipv6-cidr", "ipv6Cidr", "ipv6_cidr");
    }

    @Override
    public Object generate(Schema<?> schema) {
        int segment = CatsRandom.instance().nextInt(0xFFFF);
        int prefix = PREFIX_LENGTHS.get(CatsRandom.instance().nextInt(PREFIX_LENGTHS.size()));
        String generated = "2001:db8:%x::/%d".formatted(segment, prefix);
        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "2001:db8::/129";
    }

    @Override
    public String getTotallyWrongValue() {
        return "gggg::/999";
    }
}
