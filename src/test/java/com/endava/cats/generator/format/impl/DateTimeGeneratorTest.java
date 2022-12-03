package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DateTimeGeneratorTest {
    @Test
    void givenADateTimeFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        DateTimeGenerator strategy = new DateTimeGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("2021-07-21-T10:22:1Z");
    }


    @Test
    void givenADateTimeFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        DateTimeGenerator strategy = new DateTimeGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("1111-07-21T88:32:28Z");
    }
}
