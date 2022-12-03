package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DateGeneratorTest {

    @Test
    void givenADateFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        DateGenerator strategy = new DateGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("2021-02-30");
    }


    @Test
    void givenADateFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        DateGenerator strategy = new DateGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("11111-07-21");
    }
}
