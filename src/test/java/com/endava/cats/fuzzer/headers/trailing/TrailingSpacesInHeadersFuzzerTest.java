package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailingSpacesInHeadersFuzzerTest {
    private TrailingSpacesInHeadersFuzzer trailingSpacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingSpacesInHeadersFuzzer = new TrailingSpacesInHeadersFuzzer(null);
    }

    @Test
    void shouldReturnTrailFuzzingStrategy() {
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().getData()).isEqualTo(" ");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldMatchResponseSchema() {
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isTrue();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
