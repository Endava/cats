package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class EmailFormatGeneratorStrategyTest {
    @Test
    public void givenAEmailFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        EmailFormatGeneratorStrategy strategy = new EmailFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("email@bubu.");
    }


    @Test
    public void givenAEmailFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        EmailFormatGeneratorStrategy strategy = new EmailFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("bubulina");
    }
}
