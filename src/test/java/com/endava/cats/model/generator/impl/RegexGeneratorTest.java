package com.endava.cats.model.generator.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class RegexGeneratorTest {

    @Test
    void shouldGenerate() {
        RegexGenerator regexGenerator = new RegexGenerator();
        Assertions.assertThat(regexGenerator.generate(new Schema<>())).isEqualTo("[a-z0-9]+");
    }

    @ParameterizedTest
    @CsvSource({"regex,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        RegexGenerator regexGenerator = new RegexGenerator();
        Assertions.assertThat(regexGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
