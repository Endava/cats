package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class NoFormatGenerationStrategyTest {
    @Test
    void givenANoFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        NoFormatGenerationStrategy strategy = new NoFormatGenerationStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isNull();
    }

    @Test
    void givenANoFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        NoFormatGenerationStrategy strategy = new NoFormatGenerationStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isNull();
    }
}
