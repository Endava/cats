package com.endava.cats.fuzzer.headers;

import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EmptyStringsInHeadersFuzzerTest {
    private EmptyStringsInHeadersFuzzer emptyStringsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        emptyStringsInHeadersFuzzer = new EmptyStringsInHeadersFuzzer(null);
    }

    @Test
    void shouldReturnReplaceFuzzingStrategy() {
        Assertions.assertThat(emptyStringsInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().name()).isEqualTo(FuzzingStrategy.replace().name());
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(emptyStringsInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldMatchResponseSchema() {
        Assertions.assertThat(emptyStringsInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isTrue();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(emptyStringsInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }

    @Test
    void shouldChooseExpectedResponseBasedOnOptionalHeaderFormat() {
        var expectedResponse = emptyStringsInHeadersFuzzer.getFuzzerContext().getExpectedHttpForOptionalHeadersFuzzed();

        Assertions.assertThat(expectedResponse.apply(CatsHeader.builder().required(false).format("uuid").build()))
                .isEqualTo(ResponseCodeFamilyPredefined.FOURXX_TWOXX);
        Assertions.assertThat(expectedResponse.apply(CatsHeader.builder().required(false).format(" ").build()))
                .isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(expectedResponse.apply(CatsHeader.builder().required(true).format("uuid").build()))
                .isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
    }
}
