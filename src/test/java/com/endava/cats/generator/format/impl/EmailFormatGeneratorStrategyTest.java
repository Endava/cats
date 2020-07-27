package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class EmailFormatGeneratorStrategyTest {
    @Test
    void givenAEmailFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        EmailFormatGeneratorStrategy strategy = new EmailFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("email@bubu.");
    }


    @Test
    void givenAEmailFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        EmailFormatGeneratorStrategy strategy = new EmailFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("bubulina");
    }
}
