package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class StateCodeGeneratorTest {

    @Test
    void shouldGenerate() {
        StateCodeGenerator stateCodeGenerator = new StateCodeGenerator();
        Assertions.assertThat(stateCodeGenerator.generate(new Schema<>()).toString()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"state-code,true", "stateCode,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        StateCodeGenerator stateCodeGenerator = new StateCodeGenerator();
        Assertions.assertThat(stateCodeGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"stateCode,true", "state-code,true", "state_code,true", "other,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        StateCodeGenerator stateCodeGenerator = new StateCodeGenerator();
        Assertions.assertThat(stateCodeGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
