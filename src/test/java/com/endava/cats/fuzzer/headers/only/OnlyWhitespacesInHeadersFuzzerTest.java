package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlyWhitespacesInHeadersFuzzerTest {
    private OnlyWhitespacesInHeadersFuzzer onlyWhitespacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlyWhitespacesInHeadersFuzzer = new OnlyWhitespacesInHeadersFuzzer(null);
    }

    @Test
    void shouldReturnReplaceFuzzingStrategy() {
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo("\u1680");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldMatchResponseSchema() {
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.matchResponseSchema()).isTrue();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }
}
