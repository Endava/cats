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

@ExtendWith(SpringExtension.class)
public class ExpectOnly4XXBaseHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private ExpectOnly4XXBaseHeadersFuzzer expectOnly4XXBaseHeadersFuzzer;

    @BeforeEach
    public void setup() {
        expectOnly4XXBaseHeadersFuzzer = new My4XXFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    public void givenANewExpectOnly4XXBaseHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheExpectOnly4XXBaseHeadersFuzzer() {
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
        protected FuzzingStrategy fuzzStrategy() {
            return null;
        }

        @Override
        public String description() {
            return null;
        }
    }
}
