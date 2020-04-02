package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class NoopFuzzingStrategyTest {

    @Test
    public void givenAString_whenUsingTheNoopFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.noop().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("string");
    }
}
