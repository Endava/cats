package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class DateGeneratorTest {

    @Test
    void givenADateFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        DateGenerator strategy = new DateGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("2021-02-30");
    }

    @ParameterizedTest
    @CsvSource({"dob", "dateOfBirth", "birthdate", "otherdate"})
    void shouldNotApplyForDob(String format) {
        DateGenerator strategy = new DateGenerator();
        Assertions.assertThat(strategy.appliesTo(format, "")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"dob", "dateOfBirth", "birthdate"})
    void shouldNotApplyForDobProperties(String property) {
        DateGenerator strategy = new DateGenerator();
        Assertions.assertThat(strategy.appliesTo("date", property)).isFalse();
    }

    @Test
    void shouldApplyIfNotDobProperty() {
        DateGenerator strategy = new DateGenerator();
        Assertions.assertThat(strategy.appliesTo("date", "someOtherDate")).isTrue();
    }

    @Test
    void givenADateFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        DateGenerator strategy = new DateGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("11111-07-21");
    }
}
