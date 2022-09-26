package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.model.FuzzingStrategy;
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
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldReturnPrefixFuzzingStrategy() {
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC80");
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }
}
