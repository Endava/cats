package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates real Swift/Bic codes.
 */
@Singleton
public class BicGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "bic".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "swift".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("bic") ||
                PropertySanitizer.sanitize(propertyName).endsWith("biccode") ||
                PropertySanitizer.sanitize(propertyName).endsWith("swift") ||
                PropertySanitizer.sanitize(propertyName).endsWith("swiftcode") ||
                PropertySanitizer.sanitize(propertyName).endsWith("bankidentifiercode") ||
                PropertySanitizer.sanitize(propertyName).endsWith("bankidentifier") ||
                PropertySanitizer.sanitize(propertyName).endsWith("bankcode")
                ;
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("bic","swift");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.catsFaker().finance().bic();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}