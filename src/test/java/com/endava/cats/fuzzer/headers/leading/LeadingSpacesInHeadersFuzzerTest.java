package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LeadingSpacesInHeadersFuzzerTest {
    private LeadingSpacesInHeadersFuzzer leadingSpacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        leadingSpacesInHeadersFuzzer = new LeadingSpacesInHeadersFuzzer(null);
    }

    @Test
    void shouldExpect2xxForOptionalAndRequiredHeaders() {
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getFuzzerContext().getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getFuzzerContext().getExpectedHttpForOptionalHeadersFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
    }

    @Test
    void shouldMatchResponseSchema() {
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isTrue();
    }

    @Test
    void shouldReturnPrefixFuzzingStrategy() {
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().getData()).isEqualTo(" ");
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(1).getData()).isEqualTo("\u0009");
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy()).hasSize(2);

    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
