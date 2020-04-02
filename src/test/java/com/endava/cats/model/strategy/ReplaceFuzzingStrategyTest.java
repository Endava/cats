package com.endava.cats.model.strategy;

import com.endava.cats.model.FuzzingStrategy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReplaceFuzzingStrategyTest {

    @Test
    public void givenAString_whenUsingTheReplaceFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("inner");
    }
}
