package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class IPV6FormatGenerationStrategyTest {
    @Test
    public void givenAIPV6FormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        IPV6FormatGenerationStrategy strategy = new IPV6FormatGenerationStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("2001:db8:85a3:8d3:1319:8a2e:370:99999");
    }

    @Test
    public void givenAIPV6FormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        IPV6FormatGenerationStrategy strategy = new IPV6FormatGenerationStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("2001:db8:85a3");
    }
}
