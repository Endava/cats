package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class Bcp47GeneratorTest {

    @Test
    void shouldGenerate() {
        Bcp47Generator bcp47Generator = new Bcp47Generator();
        Assertions.assertThat(bcp47Generator.generate(new Schema<>()).toString()).hasSize(5);
    }

    @ParameterizedTest
    @CsvSource({"bcp47,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        Bcp47Generator bcp47Generator = new Bcp47Generator();
        Assertions.assertThat(bcp47Generator.appliesTo(format, "")).isEqualTo(expected);
    }

    @Test
    void shouldReturnWrongValue() {
        Bcp47Generator bcp47Generator = new Bcp47Generator();
        Assertions.assertThat(bcp47Generator.getAlmostValidValue()).isEqualTo("ro-US");
    }

    @Test
    void shouldReturnTotallyWrongValue() {
        Bcp47Generator bcp47Generator = new Bcp47Generator();
        Assertions.assertThat(bcp47Generator.getTotallyWrongValue()).isEqualTo("xx-XX");
    }
}
