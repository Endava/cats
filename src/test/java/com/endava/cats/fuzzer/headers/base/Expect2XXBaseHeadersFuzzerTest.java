package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class Expect2XXBaseHeadersFuzzerTest {
    private Expect2XXBaseHeadersFuzzer expect2XXBaseHeadersFuzzer;

    @BeforeEach
    void setup() {
        expect2XXBaseHeadersFuzzer = new My2XXFuzzer(null);
    }

    @Test
    void shouldOverrideExpectedResponseCodesWith2xx() {
        Assertions.assertThat(expect2XXBaseHeadersFuzzer.getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(expect2XXBaseHeadersFuzzer.getExpectedHttpForOptionalHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(expect2XXBaseHeadersFuzzer).hasToString(expect2XXBaseHeadersFuzzer.getClass().getSimpleName());
    }

    static class My2XXFuzzer extends Expect2XXBaseHeadersFuzzer {

        public My2XXFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
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
