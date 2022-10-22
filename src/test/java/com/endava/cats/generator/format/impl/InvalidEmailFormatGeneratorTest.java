package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InvalidEmailFormatGeneratorTest {
    @Test
    void givenAEmailFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        InvalidEmailFormatGenerator strategy = new InvalidEmailFormatGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("email@bubu.");
    }


    @Test
    void givenAEmailFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        InvalidEmailFormatGenerator strategy = new InvalidEmailFormatGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("bubulina");
    }
}
