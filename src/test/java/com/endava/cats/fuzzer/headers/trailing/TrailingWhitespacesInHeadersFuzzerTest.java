package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailingWhitespacesInHeadersFuzzerTest {
    private TrailingWhitespacesInHeadersFuzzer trailingWhitespacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingWhitespacesInHeadersFuzzer = new TrailingWhitespacesInHeadersFuzzer(null);
    }

    @Test
    void shouldReturnTrailFuzzingStrategy() {
        Assertions.assertThat(trailingWhitespacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingWhitespacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().getData()).isEqualTo("\u1680");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(trailingWhitespacesInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldMatchResponseSchema() {
        Assertions.assertThat(trailingWhitespacesInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isTrue();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(trailingWhitespacesInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
