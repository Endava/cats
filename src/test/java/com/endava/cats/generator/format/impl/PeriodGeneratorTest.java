package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Period;

@QuarkusTest
class PeriodGeneratorTest {

    @Test
    void shouldGenerate() {
        PeriodGenerator periodGenerator = new PeriodGenerator();
        Assertions.assertThatNoException().isThrownBy(() -> Period.parse(periodGenerator.generate(new Schema<>()).toString()));
    }

    @ParameterizedTest
    @CsvSource({"period,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        PeriodGenerator periodGenerator = new PeriodGenerator();
        Assertions.assertThat(periodGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
