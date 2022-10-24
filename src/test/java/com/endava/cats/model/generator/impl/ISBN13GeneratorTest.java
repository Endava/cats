package com.endava.cats.model.generator.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class ISBN13GeneratorTest {

    @Test
    void shouldGenerate() {
        ISBN13Generator isbn13Generator = new ISBN13Generator();
        Assertions.assertThat(isbn13Generator.generate(new Schema<>())).isEqualTo("9780439023481");
    }

    @ParameterizedTest
    @CsvSource({"isbn13,not,true", "not,isbn13,true", "not,not,false"})
    void shouldApply(String format, String property, boolean expected) {
        ISBN13Generator isbn13Generator = new ISBN13Generator();
        Assertions.assertThat(isbn13Generator.appliesTo(format, property)).isEqualTo(expected);
    }
}
