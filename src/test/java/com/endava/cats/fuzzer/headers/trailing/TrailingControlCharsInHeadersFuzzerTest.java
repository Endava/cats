package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

@QuarkusTest
class TrailingControlCharsInHeadersFuzzerTest {
    private TrailingControlCharsInHeadersFuzzer trailingControlCharsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingControlCharsInHeadersFuzzer = new TrailingControlCharsInHeadersFuzzer(null);
    }

    @Test
    void shouldReturnTrailFuzzingStrategy() {
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(1).getData()).isEqualTo("\u0000");
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().stream().map(fuzzingStrategy ->
                String.valueOf(fuzzingStrategy.getData())).collect(Collectors.toSet())).doesNotContain("\r");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
