package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LeadingSingleCodePointEmojisInHeadersFuzzerTest {
    private LeadingSingleCodePointEmojisInHeadersFuzzer leadingSingleCodePointEmojisInHeadersFuzzer;

    @BeforeEach
    void setup() {
        leadingSingleCodePointEmojisInHeadersFuzzer = new LeadingSingleCodePointEmojisInHeadersFuzzer(null);
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isFalse();
    }

    @Test
    void shouldReturnPrefixFuzzingStrategy() {
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC80");
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
