package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InvalidUUIDFormatGeneratorTest {
    @Test
    void givenAUUIDFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        InvalidUUIDFormatGenerator strategy = new InvalidUUIDFormatGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("123e4567-e89b-22d3-a456-42665544000");
    }

    @Test
    void givenAUUIDFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        InvalidUUIDFormatGenerator strategy = new InvalidUUIDFormatGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("123e4567");
    }
}
