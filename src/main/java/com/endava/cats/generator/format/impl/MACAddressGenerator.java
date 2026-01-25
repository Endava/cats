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
 * Generates valid MAC addresses.
 */
@Singleton
public class MACAddressGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    private static final String HEX_CHARS = "0123456789ABCDEF";

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "mac".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "macaddress".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("mac") ||
                PropertySanitizer.sanitize(propertyName).endsWith("macaddress") ||
                PropertySanitizer.sanitize(propertyName).endsWith("physicaladdress") ||
                PropertySanitizer.sanitize(propertyName).endsWith("hardwareaddress");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("mac", "macAddress", "mac-address", "mac_address");
    }

    @Override
    public Object generate(Schema<?> schema) {
        StringBuilder mac = new StringBuilder();
        
        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                mac.append(':');
            }
            mac.append(HEX_CHARS.charAt(CatsRandom.instance().nextInt(16)));
            mac.append(HEX_CHARS.charAt(CatsRandom.instance().nextInt(16)));
        }
        
        String generated = mac.toString();
        
        return DataFormat.matchesPatternOrNullWithCombinations(schema, generated);
    }

    @Override
    public String getAlmostValidValue() {
        return "00:1A:2B:3C:4D";
    }

    @Override
    public String getTotallyWrongValue() {
        return "ZZ:ZZ:ZZ:ZZ:ZZ:ZZ";
    }
}
