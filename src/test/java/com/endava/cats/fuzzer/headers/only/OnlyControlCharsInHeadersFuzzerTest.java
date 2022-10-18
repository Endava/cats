package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlyControlCharsInHeadersFuzzerTest {
    private OnlyControlCharsInHeadersFuzzer onlyControlCharsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlyControlCharsInHeadersFuzzer = new OnlyControlCharsInHeadersFuzzer(null);
    }

    @Test
    void shouldHaveReplaceFuzzingStrategy() {
        Assertions.assertThat(onlyControlCharsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlyControlCharsInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(onlyControlCharsInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(onlyControlCharsInHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(onlyControlCharsInHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }
}
