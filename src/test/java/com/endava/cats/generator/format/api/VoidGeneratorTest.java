package com.endava.cats.generator.format.api;

import com.endava.cats.generator.format.impl.VoidGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VoidGeneratorTest {


    @Test
    void shouldGenerateNull() {
        VoidGenerator voidGenerator = new VoidGenerator();
        Assertions.assertThat(voidGenerator.generate(new Schema<>())).isNull();
    }

    @Test
    void shouldAlwaysApply() {
        VoidGenerator voidGenerator = new VoidGenerator();
        Assertions.assertThat(voidGenerator.appliesTo("1", "2")).isTrue();
    }

    @Test
    void shouldGenerateNullTotallyWrongValue() {
        VoidGenerator voidGenerator = new VoidGenerator();
        Assertions.assertThat(voidGenerator.getTotallyWrongValue()).isNull();
    }

    @Test
    void shouldGenerateNullAlmostWrongValue() {
        VoidGenerator voidGenerator = new VoidGenerator();
        Assertions.assertThat(voidGenerator.getAlmostValidValue()).isNull();
    }
}
