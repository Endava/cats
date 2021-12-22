package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailingSingleCodePointEmojisHeadersFuzzerTest {
    private TrailingSingleCodePointEmojisHeadersFuzzer trailingSingleCodePointEmojisHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingSingleCodePointEmojisHeadersFuzzer = new TrailingSingleCodePointEmojisHeadersFuzzer(null, null);
    }

    @Test
    void givenANewTrailingSpacesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTrailingSpacesInHeadersFuzzer() {
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC80");
        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.getInvisibleChars()).doesNotContain("\r");

        Assertions.assertThat(trailingSingleCodePointEmojisHeadersFuzzer.matchResponseSchema()).isFalse();
    }
}
