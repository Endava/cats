package com.endava.cats.model.generator.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class RelativeJsonPointerGeneratorTest {

    @Test
    void shouldGenerate() {
        RelativeJsonPointerGenerator relativeJsonPointerGenerator = new RelativeJsonPointerGenerator();
        Assertions.assertThat(relativeJsonPointerGenerator.generate(new Schema<>())).isEqualTo("1/id");
    }

    @ParameterizedTest
    @CsvSource({"relative-json-pointer,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        RelativeJsonPointerGenerator relativeJsonPointerGenerator = new RelativeJsonPointerGenerator();
        Assertions.assertThat(relativeJsonPointerGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
