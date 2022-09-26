package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class Expect4XXBaseHeadersFuzzerTest {
    private Expect4XXBaseHeadersFuzzer expect4XXBaseHeadersFuzzer;

    @BeforeEach
    void setup() {
        expect4XXBaseHeadersFuzzer = new My4XXFuzzer(null);
    }

    @Test
    void shouldHaveResponseCode4xxForRequired() {
        Assertions.assertThat(expect4XXBaseHeadersFuzzer.getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
    }

    @Test
    void shouldHaveResponseCode2xxForOptional() {
        Assertions.assertThat(expect4XXBaseHeadersFuzzer.getExpectedHttpForOptionalHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(expect4XXBaseHeadersFuzzer).hasToString(expect4XXBaseHeadersFuzzer.getClass().getSimpleName());
    }

    static class My4XXFuzzer extends Expect4XXBaseHeadersFuzzer {

        public My4XXFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
            super(headersIteratorExecutor);
        }

        @Override
        protected String typeOfDataSentToTheService() {
            return null;
        }

        @Override
        protected List<FuzzingStrategy> fuzzStrategy() {
            return null;
        }

        @Override
        public String description() {
            return null;
        }
    }
}
