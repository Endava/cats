package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

@ExtendWith(SpringExtension.class)
class Expect4XXBaseHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private Expect4XXBaseHeadersFuzzer expect4XXBaseHeadersFuzzer;

    @BeforeEach
    void setup() {
        expect4XXBaseHeadersFuzzer = new My4XXFuzzer(serviceCaller, testCaseListener);
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
