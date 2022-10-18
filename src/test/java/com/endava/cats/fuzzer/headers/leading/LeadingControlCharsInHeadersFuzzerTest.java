package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LeadingControlCharsInHeadersFuzzerTest {
    private LeadingControlCharsInHeadersFuzzer leadingControlCharsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        leadingControlCharsInHeadersFuzzer = new LeadingControlCharsInHeadersFuzzer(null);
    }

    @Test
    void shouldNOtMatchResponseSchema() {
        Assertions.assertThat(leadingControlCharsInHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldReturnPrefixFuzzingStrategy() {
        Assertions.assertThat(leadingControlCharsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingControlCharsInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(leadingControlCharsInHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(leadingControlCharsInHeadersFuzzer.description()).isNotBlank();
    }
}
