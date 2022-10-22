package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InvalidIPV4FormatGeneratorTest {
    @Test
    void givenAIPV4FormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        InvalidIPV4FormatGenerator strategy = new InvalidIPV4FormatGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("10.10.10.300");
    }

    @Test
    void givenAIPV4FormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        InvalidIPV4FormatGenerator strategy = new InvalidIPV4FormatGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("255.");
    }
}
