package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Generates valid and invalid ISO 18245 Merchant Category Codes (MCC).
 */
@Singleton
public class MerchantCategoryCodeGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final List<String> ACTIVE_MCCS = List.of(
            "0742", "1520", "4111", "4121", "4511", "4812", "4814", "5200",
            "5311", "5411", "5541", "5651", "5732", "5812", "5814", "5912",
            "5942", "5999", "6011", "7011", "7372", "7995", "8011", "8999");
    private static final Set<String> FORMATS = Set.of("mcc", "iso18245", "merchantcategorycode");

    @Override
    public boolean appliesTo(String format, String propertyName) {
        String sanitizedFormat = PropertySanitizer.sanitize(format);
        String sanitizedProperty = PropertySanitizer.sanitize(propertyName);
        return FORMATS.contains(sanitizedFormat)
                || sanitizedProperty.endsWith("mcc")
                || sanitizedProperty.endsWith("merchantcategorycode");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("mcc", "iso18245", "merchantCategoryCode", "merchant-category-code", "merchant_category_code");
    }

    @Override
    public Object generate(Schema<?> schema) {
        return DataFormat.matchesPatternOrNull(schema, CatsUtil.selectRandom(ACTIVE_MCCS));
    }

    @Override
    public String getAlmostValidValue() {
        return "0000";
    }

    @Override
    public String getTotallyWrongValue() {
        return "99A9";
    }
}
