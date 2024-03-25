package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class BusinessNameGeneratorTest {
    @Test
    void shouldGenerate() {
        BusinessNameGenerator businessNameGenerator = new BusinessNameGenerator();
        Assertions.assertThat(businessNameGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"company,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        BusinessNameGenerator businessNameGenerator = new BusinessNameGenerator();
        Assertions.assertThat(businessNameGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"businessName,true", "BUSINESSNAME,true", "businessName,true", "business_name,true",
            "other#company,true", "other#companyName,true", "other#businessLegalName,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        BusinessNameGenerator businessNameGenerator = new BusinessNameGenerator();
        Assertions.assertThat(businessNameGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
