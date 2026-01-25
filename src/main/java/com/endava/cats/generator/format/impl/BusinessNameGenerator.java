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
 * Generates real world company names.
 */
@Singleton
public class BusinessNameGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "company".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("company") ||
                PropertySanitizer.sanitize(propertyName).endsWith("companyname") ||
                PropertySanitizer.sanitize(propertyName).endsWith("businessname") ||
                PropertySanitizer.sanitize(propertyName).endsWith("businesslegalname");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("company", "companyName", "company-name", "company_name",
                "businessName", "business-name", "business_name",
                "businessLegalName", "business-legal-name", "business_legal_name");
    }

    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.catsFaker().company().name();

        return DataFormat.matchesPatternOrNull(schema, generated);
    }
}