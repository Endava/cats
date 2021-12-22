package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlyMultiCodePointEmojisInHeadersFuzzerTest {
    private OnlyMultiCodePointEmojisInHeadersFuzzer onlyMultiCodePointEmojisInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlyMultiCodePointEmojisInHeadersFuzzer = new OnlyMultiCodePointEmojisInHeadersFuzzer(null, null);
    }

    @Test
    void givenANewSpacesOnlyInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheSpacesOnlyInHeadersFuzzer() {
        Assertions.assertThat(onlyMultiCodePointEmojisInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyMultiCodePointEmojisInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyMultiCodePointEmojisInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlyMultiCodePointEmojisInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC68\u200D\uD83C\uDFED️");
        Assertions.assertThat(onlyMultiCodePointEmojisInHeadersFuzzer.matchResponseSchema()).isFalse();
    }
}
