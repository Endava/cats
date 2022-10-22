package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InvalidHostnameFormatGeneratorTest {
    @Test
    void givenAHostnameFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        InvalidHostnameFormatGenerator strategy = new InvalidHostnameFormatGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("cool.cats.");
    }


    @Test
    void givenAHostnameFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        InvalidHostnameFormatGenerator strategy = new InvalidHostnameFormatGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("aaa111-aaaaa---");
    }
}
