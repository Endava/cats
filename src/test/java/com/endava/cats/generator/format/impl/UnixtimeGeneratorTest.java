package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

@QuarkusTest
class UnixtimeGeneratorTest {

    @Test
    void shouldGenerate() {
        UnixtimeGenerator unixtimeGenerator = new UnixtimeGenerator();
        Object generated = unixtimeGenerator.generate(new Schema<>());
        Instant generatedInstant = Instant.ofEpochSecond((long) generated);
        Assertions.assertThat(generated).isInstanceOf(Long.class);
        Assertions.assertThat(generatedInstant).isBefore(Instant.now());
    }

    @ParameterizedTest
    @CsvSource({"unixtime,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        UnixtimeGenerator unixtimeGenerator = new UnixtimeGenerator();
        Assertions.assertThat(unixtimeGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
