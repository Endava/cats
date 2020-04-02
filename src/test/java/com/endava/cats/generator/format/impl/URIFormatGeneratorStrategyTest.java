package com.endava.cats.generator.format.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class URIFormatGeneratorStrategyTest {
    @Test
    public void givenAURIFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        URIFormatGeneratorStrategy strategy = new URIFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("mailto:l@s.");
    }

    @Test
    public void givenAURIFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        URIFormatGeneratorStrategy strategy = new URIFormatGeneratorStrategy();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("\"wrongURI\"");
    }
}
