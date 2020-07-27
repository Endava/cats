package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TrailFuzzingStrategyTest {
    @Test
    void givenAString_whenUsingTheTrailFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.trail().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("stringinner");
    }
}
