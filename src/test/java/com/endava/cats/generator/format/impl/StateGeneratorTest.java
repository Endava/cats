package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class StateGeneratorTest {
    @Test
    void shouldGenerate() {
        StateGenerator stateGenerator = new StateGenerator();
        Assertions.assertThat(stateGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"state,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        StateGenerator stateGenerator = new StateGenerator();
        Assertions.assertThat(stateGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"state,true", "STATE,true", "other#state,true", "statename,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        StateGenerator stateGenerator = new StateGenerator();
        Assertions.assertThat(stateGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
