package com.endava.cats.model.generator.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetTime;

@QuarkusTest
class TimeGeneratorTest {

    @ParameterizedTest
    @CsvSource({"time,true", "date-time,false"})
    void shouldApply(String format, boolean expected) {
        TimeGenerator timeGenerator = new TimeGenerator();
        Assertions.assertThat(timeGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @Test
    void shouldGenerate() {
        TimeGenerator timeGenerator = new TimeGenerator();
        Object generated = timeGenerator.generate(new Schema<>());
        Assertions.assertThatNoException().isThrownBy(() -> OffsetTime.parse(generated.toString()));
    }
}
