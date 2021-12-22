package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlySingleCodePointEmojisInHeadersFuzzerTest {
    private OnlySingleCodePointEmojisInHeadersFuzzer onlySingleCodePointEmojisInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlySingleCodePointEmojisInHeadersFuzzer = new OnlySingleCodePointEmojisInHeadersFuzzer(null, null);
    }

    @Test
    void givenANewSpacesOnlyInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheSpacesOnlyInHeadersFuzzer() {
        Assertions.assertThat(onlySingleCodePointEmojisInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(onlySingleCodePointEmojisInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlySingleCodePointEmojisInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlySingleCodePointEmojisInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC80");
        Assertions.assertThat(onlySingleCodePointEmojisInHeadersFuzzer.matchResponseSchema()).isFalse();
    }
}
