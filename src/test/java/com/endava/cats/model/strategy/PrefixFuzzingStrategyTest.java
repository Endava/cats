package com.endava.cats.model.strategy;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PrefixFuzzingStrategyTest {

    @Test
    void givenAString_whenUsingThePrefixFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.prefix().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("innerstring");
    }
}

