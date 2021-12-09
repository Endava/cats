package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailingWhitespacesInHeadersFuzzerTest {
    private TrailingWhitespacesInHeadersFuzzer trailingWhitespacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingWhitespacesInHeadersFuzzer = new TrailingWhitespacesInHeadersFuzzer(null, null);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(trailingWhitespacesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingWhitespacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(trailingWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo("\u1680");
    }
}
