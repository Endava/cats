package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class UUIDFormatGeneratorStrategyTest {
    @Test
    void givenAUUIDFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        UUIDFormatGeneratorStrategy strategy = new UUIDFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("123e4567-e89b-22d3-a456-42665544000");
    }

    @Test
    void givenAUUIDFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        UUIDFormatGeneratorStrategy strategy = new UUIDFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("123e4567");
    }
}
