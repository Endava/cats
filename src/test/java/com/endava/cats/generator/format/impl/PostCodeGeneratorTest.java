package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class PostCodeGeneratorTest {
    @Test
    void shouldGenerate() {
        PostCodeGenerator postCodeGenerator = new PostCodeGenerator();
        Assertions.assertThat(postCodeGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"zip,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        PostCodeGenerator postCodeGenerator = new PostCodeGenerator();
        Assertions.assertThat(postCodeGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"postCode,true", "postalCode,true", "zipCode,true", "pinCode,true", "zip,true", "POSTCODE,true", "postCode,true", "post_code,true", "other#postCode,true", "other,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        PostCodeGenerator postCodeGenerator = new PostCodeGenerator();
        Assertions.assertThat(postCodeGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
