package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailingMultiCodePointEmojisHeadersFuzzerTest {
    private TrailingMultiCodePointEmojisHeadersFuzzer trailingMultiCodePointEmojisHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingMultiCodePointEmojisHeadersFuzzer = new TrailingMultiCodePointEmojisHeadersFuzzer(null);
    }

    @Test
    void shouldReturnTrailFuzzingStrategy() {
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC68\u200D\uD83C\uDFED️");
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.getInvisibleChars()).doesNotContain("\r");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }
}
