package com.endava.cats.strategy;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SkipFuzzingStrategyTest {

    @Test
    void givenAString_whenUsingTheSkipFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.skip().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("inner");
    }
}
