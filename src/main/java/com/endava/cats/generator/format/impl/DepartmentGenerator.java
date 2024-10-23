package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Randomly generates departments.
 */
@Singleton
public class DepartmentGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    static final List<String> DEPARTMENTS = List.of(
            "Human Resources",
            "Finance",
            "Marketing",
            "Sales",
            "Information Technology",
            "Customer Service",
            "Research and Development",
            "Operations",
            "Logistics",
            "Production",
            "Quality Assurance",
            "Legal",
            "Procurement",
            "Public Relations",
            "Engineering",
            "Administration",
            "Training and Development",
            "Health and Safety",
            "Facilities Management",
            "Strategic Planning",
            "Business Development",
            "Compliance",
            "Investor Relations",
            "Corporate Communications",
            "Risk Management",
            "Project Management",
            "Environmental Sustainability",
            "Data Analytics",
            "Product Management",
            "Supply Chain Management"
    );
    private static final String DEPARTMENT_WORD = "department";

    @Override
    public Object generate(Schema<?> schema) {
        return CatsUtil.selectRandom(DEPARTMENTS);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return DEPARTMENT_WORD.equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase(DEPARTMENT_WORD);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of(DEPARTMENT_WORD);
    }
}
