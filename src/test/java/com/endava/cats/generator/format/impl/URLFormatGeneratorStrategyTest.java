package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class URLFormatGeneratorStrategyTest {
    @Test
    void givenAURLFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        URLFormatGeneratorStrategy strategy = new URLFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("http://catsiscool.");
    }

    @Test
    void givenAURLFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        URLFormatGeneratorStrategy strategy = new URLFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("catsiscool");
    }
}
