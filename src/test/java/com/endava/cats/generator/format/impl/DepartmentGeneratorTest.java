package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class DepartmentGeneratorTest {
    @Test
    void shouldGenerate() {
        DepartmentGenerator departmentGenerator = new DepartmentGenerator();
        Assertions.assertThat(departmentGenerator.generate(new Schema<>()).toString()).containsAnyOf(
                DepartmentGenerator.DEPARTMENTS.toArray(new String[0]));
    }

    @ParameterizedTest
    @CsvSource({"department,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        DepartmentGenerator departmentGenerator = new DepartmentGenerator();
        Assertions.assertThat(departmentGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"department,true", "DEPARTMENT,true", "other,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        DepartmentGenerator departmentGenerator = new DepartmentGenerator();
        Assertions.assertThat(departmentGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}