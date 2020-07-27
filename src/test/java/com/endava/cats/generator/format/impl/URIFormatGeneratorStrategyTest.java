package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class URIFormatGeneratorStrategyTest {
    @Test
    void givenAURIFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        URIFormatGeneratorStrategy strategy = new URIFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("mailto:l@s.");
    }

    @Test
    void givenAURIFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        URIFormatGeneratorStrategy strategy = new URIFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("\"wrongURI\"");
    }
}
