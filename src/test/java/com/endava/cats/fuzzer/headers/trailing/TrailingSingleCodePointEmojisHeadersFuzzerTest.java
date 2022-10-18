package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailingSingleCodePointEmojisHeadersFuzzerTest {
    private TrailingSingleCodePointEmojisHeadersFuzzer trailingSingleCodePointEmojisHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingSingleCodePointEmojisHeadersFuzzer = new TrailingSingleCodePointEmojisHeadersFuzzer(null);
    }

    @Test
    void shouldReturnTrailFuzzingStrategy() {
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC80");
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.getInvisibleChars()).doesNotContain("\r");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }
}
