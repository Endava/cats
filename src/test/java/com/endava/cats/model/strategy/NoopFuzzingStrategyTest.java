package com.endava.cats.model.strategy;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NoopFuzzingStrategyTest {

    @Test
    void givenAString_whenUsingTheNoopFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.noop().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("string");
        Assertions.assertThat(strategy.name()).isEqualTo("NOOP");
    }
}
