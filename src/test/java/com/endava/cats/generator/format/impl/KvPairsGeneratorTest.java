package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

@QuarkusTest
class KvPairsGeneratorTest {
    @Test
    void shouldGenerate() {
        KvPairsGenerator kvPairsGenerator = new KvPairsGenerator();
        Object generated = kvPairsGenerator.generate(new Schema<>());
        Assertions.assertThat(generated).isInstanceOf(Map.class);
    }

    @ParameterizedTest
    @CsvSource({"kvpairs,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        KvPairsGenerator kvPairsGenerator = new KvPairsGenerator();
        Assertions.assertThat(kvPairsGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
