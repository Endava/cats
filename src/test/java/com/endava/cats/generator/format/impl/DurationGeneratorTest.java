package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

@QuarkusTest
class DurationGeneratorTest {

    @Test
    void shouldGenerate() {
        DurationGenerator durationGenerator = new DurationGenerator();
        Assertions.assertThatNoException().isThrownBy(() -> Duration.parse(durationGenerator.generate(new Schema<>()).toString()));
    }

    @ParameterizedTest
    @CsvSource({"duration,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        DurationGenerator durationGenerator = new DurationGenerator();
        Assertions.assertThat(durationGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @Test
    void shouldGenerateWrongValue() {
        DurationGenerator durationGenerator = new DurationGenerator();
        Assertions.assertThat(durationGenerator.getAlmostValidValue()).isEqualTo("PT2334384");

    }

    @Test
    void shouldGenerateTotallyWrongValue() {
        DurationGenerator durationGenerator = new DurationGenerator();
        Assertions.assertThat(durationGenerator.getTotallyWrongValue()).isEqualTo("1234569");
    }
}
