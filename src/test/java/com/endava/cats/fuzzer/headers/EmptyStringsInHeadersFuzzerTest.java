package com.endava.cats.fuzzer.headers;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EmptyStringsInHeadersFuzzerTest {
    private EmptyStringsInHeadersFuzzer emptyStringsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        emptyStringsInHeadersFuzzer = new EmptyStringsInHeadersFuzzer(null);
    }

    @Test
    void shouldReturnReplaceFuzzingStrategy() {
        Assertions.assertThat(emptyStringsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(emptyStringsInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldMatchResponseSchema() {
        Assertions.assertThat(emptyStringsInHeadersFuzzer.matchResponseSchema()).isTrue();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(emptyStringsInHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }
}
