package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates medical record numbers (MRN).
 */
@Singleton
public class MedicalRecordNumberGenerator implements ValidDataFormatGenerator, OpenAPIFormat {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "mrn".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                "medicalrecordnumber".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("mrn") ||
                PropertySanitizer.sanitize(propertyName).endsWith("medicalrecordnumber") ||
                PropertySanitizer.sanitize(propertyName).endsWith("patientid") ||
                PropertySanitizer.sanitize(propertyName).endsWith("patientnumber");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("mrn", "medicalRecordNumber", "medical-record-number", "medical_record_number");
    }

    @Override
    public Object generate(Schema<?> schema) {
        int part1 = CatsRandom.instance().nextInt(900) + 100;
        int part2 = CatsRandom.instance().nextInt(900000) + 100000;

        String generated = String.format("%03d-%06d", part1, part2);

        return DataFormat.matchesPatternOrNullWithCombinations(schema, generated);
    }
}
