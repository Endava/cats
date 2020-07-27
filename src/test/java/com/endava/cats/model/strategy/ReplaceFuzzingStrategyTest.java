package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ReplaceFuzzingStrategyTest {

    @Test
    void givenAString_whenUsingTheReplaceFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("inner");
    }
}
