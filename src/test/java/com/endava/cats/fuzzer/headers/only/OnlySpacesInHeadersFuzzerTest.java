package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlySpacesInHeadersFuzzerTest {
    private OnlySpacesInHeadersFuzzer onlySpacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlySpacesInHeadersFuzzer = new OnlySpacesInHeadersFuzzer(null);
    }

    @Test
    void shouldHaveReplaceFuzzingStrategy() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlySpacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo(" ");
    }

    @Test
    void shouldReturn4xxForRequiredAnd2xxForOptionalResponseCodes() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getExpectedHttpForOptionalHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldMatchResponseSchema() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.matchResponseSchema()).isTrue();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }
}
