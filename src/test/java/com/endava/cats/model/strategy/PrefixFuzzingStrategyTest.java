package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PrefixFuzzingStrategyTest {

    @Test
    void givenAString_whenUsingThePrefixFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.prefix().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("innerstring");
    }
}

