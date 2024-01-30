package com.endava.cats.strategy;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailFuzzingStrategyTest {
    @Test
    void givenAString_whenUsingTheTrailFuzzingStrategy_thenTheStringIsCorrectlyProcessed() {
        FuzzingStrategy strategy = FuzzingStrategy.trail().withData("inner");

        Assertions.assertThat(strategy.process("string")).isEqualTo("stringinner");
    }
}
