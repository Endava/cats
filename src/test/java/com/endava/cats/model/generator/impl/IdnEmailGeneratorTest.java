package com.endava.cats.model.generator.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class IdnEmailGeneratorTest {

    @Test
    void shouldGenerate() {
        IdnEmailGenerator idnEmailGenerator = new IdnEmailGenerator();
        Assertions.assertThat(idnEmailGenerator.generate(new Schema<>()).toString()).endsWith("cööl.cats@cats.io");
    }

    @ParameterizedTest
    @CsvSource({"idn-email,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        IdnEmailGenerator idnEmailGenerator = new IdnEmailGenerator();
        Assertions.assertThat(idnEmailGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
