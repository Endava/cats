package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class IPV4FormatGenerationStrategyTest {
    @Test
    void givenAIPV4FormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        IPV4FormatGenerationStrategy strategy = new IPV4FormatGenerationStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("10.10.10.300");
    }

    @Test
    void givenAIPV4FormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        IPV4FormatGenerationStrategy strategy = new IPV4FormatGenerationStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("255.");
    }
}
