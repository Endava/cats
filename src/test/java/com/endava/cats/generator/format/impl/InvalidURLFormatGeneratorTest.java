package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InvalidURLFormatGeneratorTest {
    @Test
    void givenAURLFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        InvalidURLFormatGenerator strategy = new InvalidURLFormatGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("http://catsiscool.");
    }

    @Test
    void givenAURLFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        InvalidURLFormatGenerator strategy = new InvalidURLFormatGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("catsiscool");
    }
}
