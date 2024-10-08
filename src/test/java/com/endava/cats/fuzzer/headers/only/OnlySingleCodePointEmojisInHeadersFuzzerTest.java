package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlySingleCodePointEmojisInHeadersFuzzerTest {
    private OnlySingleCodePointEmojisInHeadersFuzzer onlySingleCodePointEmojisInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlySingleCodePointEmojisInHeadersFuzzer = new OnlySingleCodePointEmojisInHeadersFuzzer(null);
    }

    @Test
    void shouldHaveReplaceFuzzingStrategy() {
        Assertions.assertThat(onlySingleCodePointEmojisInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlySingleCodePointEmojisInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC80");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(onlySingleCodePointEmojisInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(onlySingleCodePointEmojisInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(onlySingleCodePointEmojisInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
