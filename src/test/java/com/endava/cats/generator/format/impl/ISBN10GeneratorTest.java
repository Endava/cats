package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class ISBN10GeneratorTest {

    @Test
    void shouldGenerate() {
        ISBN10Generator isbn10Generator = new ISBN10Generator();
        CatsRandom.initRandom(0);
        for (int i = 0; i < 20; i++) {
            String generated = isbn10Generator.generate(new Schema<>()).toString();
            Assertions.assertThat(generated).matches("[0-9]{10}");
            Assertions.assertThat(generated.charAt(9) - '0')
                    .isEqualTo(ISBN10Generator.computeCheckDigit(generated.substring(0, 9).toCharArray()));
        }
        Assertions.assertThat(isbn10Generator.getAlmostValidValue()).isEqualTo("0306406153");
        Assertions.assertThat(isbn10Generator.getTotallyWrongValue()).isEqualTo("INVALID-ISBN10");
    }

    @ParameterizedTest
    @CsvSource({"isbn10,not,true", "not,isbn10,true", "isbn,not,true", "not,isbn,true", "not,not,false"})
    void shouldApply(String format, String property, boolean expected) {
        ISBN10Generator isbn10Generator = new ISBN10Generator();
        Assertions.assertThat(isbn10Generator.appliesTo(format, property)).isEqualTo(expected);
    }
}
