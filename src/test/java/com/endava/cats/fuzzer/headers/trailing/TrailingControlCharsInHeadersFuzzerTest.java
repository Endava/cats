package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailingControlCharsInHeadersFuzzerTest {
    private TrailingControlCharsInHeadersFuzzer trailingControlCharsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingControlCharsInHeadersFuzzer = new TrailingControlCharsInHeadersFuzzer(null);
    }

    @Test
    void shouldReturnTrailFuzzingStrategy() {
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.getInvisibleChars()).doesNotContain("\r");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }
}
