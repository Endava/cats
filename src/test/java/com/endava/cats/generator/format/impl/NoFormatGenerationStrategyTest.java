package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class NoFormatGenerationStrategyTest {
    @Test
    public void givenANoFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        NoFormatGenerationStrategy strategy = new NoFormatGenerationStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isNull();
    }

    @Test
    public void givenANoFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        NoFormatGenerationStrategy strategy = new NoFormatGenerationStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isNull();
    }
}
