package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NoFormatGeneratorTest {
    @Test
    void givenANoFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        NoFormatGenerator strategy = new NoFormatGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isNull();
    }

    @Test
    void givenANoFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        NoFormatGenerator strategy = new NoFormatGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isNull();
    }
}
