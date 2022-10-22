package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InvalidURIFormatGeneratorTest {
    @Test
    void givenAURIFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        InvalidURIFormatGenerator strategy = new InvalidURIFormatGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("mailto:l@s.");
    }

    @Test
    void givenAURIFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        InvalidURIFormatGenerator strategy = new InvalidURIFormatGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("\"wrongURI\"");
    }
}
