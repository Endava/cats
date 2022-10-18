package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.http.ResponseCodeFamily;
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
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.getExpectedHttpForOptionalHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
    }

    @Test
    void shouldMatchResponseSchema() {
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.matchResponseSchema()).isTrue();
    }

    @Test
    void shouldReturnPrefixFuzzingStrategy() {
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo(" ");
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0009");
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.fuzzStrategy()).hasSize(2);

    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(leadingSpacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }
}
