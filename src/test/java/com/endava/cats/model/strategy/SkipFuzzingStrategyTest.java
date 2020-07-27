package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SkipFuzzingStrategyTest {

    @Test
    void givenAString_whenUsingTheSkipFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.skip().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("inner");
    }
}
