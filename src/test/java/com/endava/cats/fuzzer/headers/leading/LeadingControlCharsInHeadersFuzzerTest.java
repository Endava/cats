package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LeadingControlCharsInHeadersFuzzerTest {
    private LeadingControlCharsInHeadersFuzzer leadingControlCharsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        leadingControlCharsInHeadersFuzzer = new LeadingControlCharsInHeadersFuzzer(null, null);
    }

    @Test
    void givenANewLeadingSpacesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingSpacesInHeadersFuzzer() {
        Assertions.assertThat(leadingControlCharsInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingControlCharsInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(leadingControlCharsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingControlCharsInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");
        Assertions.assertThat(leadingControlCharsInHeadersFuzzer.matchResponseSchema()).isFalse();
    }
}
