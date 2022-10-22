package com.endava.cats.model.generator.api;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VoidGeneratorTest {


    @Test
    void shouldGenerateNull() {
        ValidDataFormatGenerator.VoidGenerator voidGenerator = new ValidDataFormatGenerator.VoidGenerator();
        Assertions.assertThat(voidGenerator.generate(new Schema<>())).isNull();
    }

    @Test
    void shouldAlwaysApply() {
        ValidDataFormatGenerator.VoidGenerator voidGenerator = new ValidDataFormatGenerator.VoidGenerator();
        Assertions.assertThat(voidGenerator.appliesTo("1", "2")).isTrue();
    }
}
