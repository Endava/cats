package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.impl.JsonPointerGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class JsonPointerGeneratorTest {

    @Test
    void shouldGenerate() {
        JsonPointerGenerator jsonPointerGenerator = new JsonPointerGenerator();
        Assertions.assertThat(jsonPointerGenerator.generate(new Schema<>())).isEqualTo("/item/0/id");
    }

    @ParameterizedTest
    @CsvSource({"json-pointer,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        JsonPointerGenerator jsonPointerGenerator = new JsonPointerGenerator();
        Assertions.assertThat(jsonPointerGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
