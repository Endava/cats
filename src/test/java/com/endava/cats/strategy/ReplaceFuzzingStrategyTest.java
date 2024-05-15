package com.endava.cats.strategy;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ReplaceFuzzingStrategyTest {

    @Test
    void givenAString_whenUsingTheReplaceFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.replace().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("inner");
    }
}
