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
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC80");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
