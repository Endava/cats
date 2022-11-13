package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class Gtin8GeneratorTest {

    @Test
    void shouldGenerate() {
        Gtin8Generator gtin8Generator = new Gtin8Generator();
        Assertions.assertThat(gtin8Generator.generate(new Schema<>()).toString()).matches("[0-9]+").hasSize(8);
    }

    @ParameterizedTest
    @CsvSource({"ean8,true", "gtin8,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        Gtin8Generator gtin8Generator = new Gtin8Generator();
        Assertions.assertThat(gtin8Generator.appliesTo(format, "")).isEqualTo(expected);
    }
}
