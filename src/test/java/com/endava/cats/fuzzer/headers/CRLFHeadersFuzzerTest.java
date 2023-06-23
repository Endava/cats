package com.endava.cats.fuzzer.headers;


import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class CRLFHeadersFuzzerTest {
    private CRLFHeadersFuzzer crlfHeadersFuzzer;

    @BeforeEach
    void setup() {
        crlfHeadersFuzzer = new CRLFHeadersFuzzer(Mockito.mock(HeadersIteratorExecutor.class));
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(crlfHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(crlfHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveReplaceFuzzingStrategy() {
        Assertions.assertThat(crlfHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(0).name()).isEqualTo("REPLACE");
    }

    @Test
    void shouldReturnCrLfInvisibleChars() {
        Assertions.assertThat(crlfHeadersFuzzer.getFuzzerContext().getFuzzStrategy()).hasSize(1);
        Assertions.assertThat(crlfHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(0).getData().toString()).isEqualTo("\r\n");
    }

    @Test
    void shouldReturnTypeOfDataToSend() {
        Assertions.assertThat(crlfHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isEqualTo("CR & LF characters");
    }

}
