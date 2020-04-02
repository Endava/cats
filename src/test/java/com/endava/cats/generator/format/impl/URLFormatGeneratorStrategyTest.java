package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class URLFormatGeneratorStrategyTest {
    @Test
    public void givenAURLFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        URLFormatGeneratorStrategy strategy = new URLFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("http://catsiscool.");
    }

    @Test
    public void givenAURLFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        URLFormatGeneratorStrategy strategy = new URLFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("catsiscool");
    }
}
