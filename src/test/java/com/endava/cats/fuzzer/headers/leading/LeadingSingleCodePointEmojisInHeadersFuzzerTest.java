package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LeadingSingleCodePointEmojisInHeadersFuzzerTest {
    private LeadingSingleCodePointEmojisInHeadersFuzzer leadingSingleCodePointEmojisInHeadersFuzzer;

    @BeforeEach
    void setup() {
        leadingSingleCodePointEmojisInHeadersFuzzer = new LeadingSingleCodePointEmojisInHeadersFuzzer(null, null);
    }

    @Test
    void givenANewLeadingSpacesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingSpacesInHeadersFuzzer() {
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC80");
        Assertions.assertThat(leadingSingleCodePointEmojisInHeadersFuzzer.matchResponseSchema()).isFalse();
    }
}
