package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class TrailFuzzingStrategyTest {
    @Test
    public void givenAString_whenUsingTheTrailFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.trail().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("stringinner");
    }
}
