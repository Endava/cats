package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates real addresses.
 */
@Singleton
public class AddressGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    private static final String HEX_CHARS = "0123456789abcdef";
    private static final List<String> HEX_ADDRESS_PREFIXES = List.of(
            "contract", "wallet", "token", "blockchain", "crypto", "eth", "owner", "spender", "from", "to");

    private boolean generateHexAddress;

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        this.generateHexAddress = isHexAddressProperty(sanitizedProperty);
        return ("address".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                sanitizedProperty.endsWith("address"))
                && !sanitizedProperty.contains("email");
    }

    private static boolean isHexAddressProperty(String sanitizedProperty) {
        return sanitizedProperty.endsWith("address") &&
                HEX_ADDRESS_PREFIXES.stream().anyMatch(prefix -> sanitizedProperty.endsWith(prefix + "address"));
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("address");
    }

    @Override
    public Object generate(Schema<?> schema) {
        if (this.generateHexAddress) {
            StringBuilder hex = new StringBuilder("0x");
            for (int i = 0; i < 40; i++) {
                hex.append(HEX_CHARS.charAt(CatsRandom.instance().nextInt(HEX_CHARS.length())));
            }
            return DataFormat.matchesPatternOrNull(schema, hex.toString());
        }
        String generated = CatsUtil.catsFaker().address().fullAddress();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}