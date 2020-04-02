package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class HostnameFormatGeneratorStrategyTest {
    @Test
    public void givenAHostnameFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        HostnameFormatGeneratorStrategy strategy = new HostnameFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("cool.cats.");
    }


    @Test
    public void givenAHostnameFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        HostnameFormatGeneratorStrategy strategy = new HostnameFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("aaa111-aaaaa---");
    }
}
