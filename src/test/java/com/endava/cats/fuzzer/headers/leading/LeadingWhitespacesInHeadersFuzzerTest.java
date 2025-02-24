package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LeadingWhitespacesInHeadersFuzzerTest {
    private LeadingWhitespacesInHeadersFuzzer leadingWhitespacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        leadingWhitespacesInHeadersFuzzer = new LeadingWhitespacesInHeadersFuzzer(null);
    }

    @Test
    void shouldHavePrefixFuzzingStrategy() {
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().getData()).isEqualTo("\u1680");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldMatchResponseSchema() {
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isTrue();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
