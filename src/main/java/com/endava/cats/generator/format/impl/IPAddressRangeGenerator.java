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
import java.util.Set;

/**
 * Generates IP address ranges in CIDR notation.
 */
@Singleton
public class IPAddressRangeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final Set<String> FORMATS = Set.of("cidr", "iprange", "ipv4cidr");
    private static final List<String> IPV6_SUFFIXES = List.of("ipv6cidr", "ipv6range", "ipv6subnet");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        if ("ipv6cidr".equalsIgnoreCase(sanitizedFormat)
                || IPV6_SUFFIXES.stream().anyMatch(sanitizedProperty::endsWith)) {
            return false;
        }
        return FORMATS.contains(sanitizedFormat) ||
                sanitizedProperty.endsWith("cidr") ||
                sanitizedProperty.endsWith("iprange") ||
                sanitizedProperty.endsWith("subnet") ||
                sanitizedProperty.endsWith("networkrange");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("cidr", "ipv4-cidr", "ipRange", "ip-range", "ip_range", "subnet");
    }

    @Override
    public Object generate(Schema<?> schema) {
        int octet1 = CatsRandom.instance().nextInt(224) + 1;
        int octet2 = CatsRandom.instance().nextInt(256);
        int octet3 = CatsRandom.instance().nextInt(256);
        int octet4 = 0;
        int cidrBits = CatsRandom.instance().nextInt(9) + 16;

        String generated = String.format("%d.%d.%d.%d/%d", octet1, octet2, octet3, octet4, cidrBits);

        return DataFormat.matchesPatternOrNull(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "192.168.1.0/33";
    }

    @Override
    public String getTotallyWrongValue() {
        return "999.999.999.999/99";
    }
}
