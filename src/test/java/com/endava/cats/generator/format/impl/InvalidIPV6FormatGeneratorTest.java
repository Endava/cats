package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InvalidIPV6FormatGeneratorTest {
    @Test
    void givenAIPV6FormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        InvalidIPV6FormatGenerator strategy = new InvalidIPV6FormatGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("2001:db8:85a3:8d3:1319:8a2e:370:99999");
    }

    @Test
    void givenAIPV6FormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        InvalidIPV6FormatGenerator strategy = new InvalidIPV6FormatGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("2001:db8:85a3");
    }
}
