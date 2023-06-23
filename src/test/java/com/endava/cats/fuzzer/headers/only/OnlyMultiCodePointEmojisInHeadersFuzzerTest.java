package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlyMultiCodePointEmojisInHeadersFuzzerTest {
    private OnlyMultiCodePointEmojisInHeadersFuzzer onlyMultiCodePointEmojisInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlyMultiCodePointEmojisInHeadersFuzzer = new OnlyMultiCodePointEmojisInHeadersFuzzer(null);
    }

    @Test
    void shouldHaveReplaceFuzzingStrategy() {
        Assertions.assertThat(onlyMultiCodePointEmojisInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlyMultiCodePointEmojisInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC68\u200D\uD83C\uDFED️");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(onlyMultiCodePointEmojisInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(onlyMultiCodePointEmojisInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(onlyMultiCodePointEmojisInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
