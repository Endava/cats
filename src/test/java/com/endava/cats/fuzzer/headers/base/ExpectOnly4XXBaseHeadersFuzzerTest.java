package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class ExpectOnly4XXBaseHeadersFuzzerTest {
    private ExpectOnly4XXBaseHeadersFuzzer expectOnly4XXBaseHeadersFuzzer;

    @BeforeEach
    void setup() {
        expectOnly4XXBaseHeadersFuzzer = new My4XXFuzzer(null, null);
    }

    @Test
    void givenANewExpectOnly4XXBaseHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheExpectOnly4XXBaseHeadersFuzzer() {
        Assertions.assertThat(expectOnly4XXBaseHeadersFuzzer.getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(expectOnly4XXBaseHeadersFuzzer.getExpectedHttpForOptionalHeadersFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(expectOnly4XXBaseHeadersFuzzer).hasToString(expectOnly4XXBaseHeadersFuzzer.getClass().getSimpleName());
    }

    static class My4XXFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {

        public My4XXFuzzer(ServiceCaller sc, TestCaseListener lr) {
            super(sc, lr);
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
