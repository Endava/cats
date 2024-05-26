package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class ZeroWidthCharsInValuesHeadersFuzzerTest {
    private ZeroWidthCharsInValuesHeadersFuzzer zeroWidthCharsInValuesHeadersFuzzer;

    @BeforeEach
    void setup() {
        zeroWidthCharsInValuesHeadersFuzzer = new ZeroWidthCharsInValuesHeadersFuzzer(Mockito.mock(HeadersIteratorExecutor.class));
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(zeroWidthCharsInValuesHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(zeroWidthCharsInValuesHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveInsertFuzzingStrategy() {
        Assertions.assertThat(zeroWidthCharsInValuesHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(0).name()).isEqualTo("INSERT");
    }

    @Test
    void shouldReturnZeroWidthChars() {
        Assertions.assertThat(zeroWidthCharsInValuesHeadersFuzzer.getFuzzerContext().getFuzzStrategy()).hasSize(11);
        Assertions.assertThat(zeroWidthCharsInValuesHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(0).getData()).hasToString("\u200b");
    }

    @Test
    void shouldReturnTypeOfDataToSend() {
        Assertions.assertThat(zeroWidthCharsInValuesHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isEqualTo("Zero-width characters");
    }
}
