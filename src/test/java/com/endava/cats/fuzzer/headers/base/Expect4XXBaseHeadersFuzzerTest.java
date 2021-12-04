package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
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
        expect4XXBaseHeadersFuzzer = new My4XXFuzzer(null, null);
    }

    @Test
    void givenANewExpect4XXBaseHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheExpect4XXBaseHeadersFuzzer() {
        Assertions.assertThat(expect4XXBaseHeadersFuzzer.getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(expect4XXBaseHeadersFuzzer.getExpectedHttpForOptionalHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(expect4XXBaseHeadersFuzzer).hasToString(expect4XXBaseHeadersFuzzer.getClass().getSimpleName());
    }

    static class My4XXFuzzer extends Expect4XXBaseHeadersFuzzer {

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
