package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class UUIDGeneratorTest {
    @Test
    void givenAUUIDFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        UUIDGenerator strategy = new UUIDGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("123e4567-e89b-22d3-a456-42665544000");
    }

    @Test
    void givenAUUIDFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        UUIDGenerator strategy = new UUIDGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("123e4567");
    }
}
