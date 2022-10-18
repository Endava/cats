package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LeadingMultiCodePointEmojisInHeadersFuzzerTest {
    private LeadingMultiCodePointEmojisInHeadersFuzzer leadingMultiCodePointEmojisInHeadersFuzzer;

    @BeforeEach
    void setup() {
        leadingMultiCodePointEmojisInHeadersFuzzer = new LeadingMultiCodePointEmojisInHeadersFuzzer(null);
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(leadingMultiCodePointEmojisInHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldReturnPrefixFuzzingStrategy() {
        Assertions.assertThat(leadingMultiCodePointEmojisInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingMultiCodePointEmojisInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC68\u200D\uD83C\uDFED️");
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(leadingMultiCodePointEmojisInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(leadingMultiCodePointEmojisInHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }
}
