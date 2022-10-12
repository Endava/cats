package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@QuarkusTest
class DateTimeFormatGeneratorStrategyTest {
    @Test
    void givenADateTimeFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        DateTimeFormatGeneratorStrategy strategy = new DateTimeFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:'Z'").format(LocalDateTime.now(ZoneId.systemDefault())));
    }


    @Test
    void givenADateTimeFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        DateTimeFormatGeneratorStrategy strategy = new DateTimeFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("1000-07-21T88:32:28Z");
    }
}
