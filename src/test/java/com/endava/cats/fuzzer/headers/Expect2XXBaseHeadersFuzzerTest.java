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
public class Expect2XXBaseHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private Expect2XXBaseHeadersFuzzer expect2XXBaseHeadersFuzzer;

    @BeforeEach
    public void setup() {
        expect2XXBaseHeadersFuzzer = new My2XXFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    public void givenANewExpect2XXBaseHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheExpect2XXBaseHeadersFuzzer() {
        Assertions.assertThat(expect2XXBaseHeadersFuzzer.getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(expect2XXBaseHeadersFuzzer.getExpectedHttpForOptionalHeadersFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(expect2XXBaseHeadersFuzzer).hasToString(expect2XXBaseHeadersFuzzer.getClass().getSimpleName());
    }

    static class My2XXFuzzer extends Expect2XXBaseHeadersFuzzer {

        public My2XXFuzzer(ServiceCaller sc, TestCaseListener lr) {
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
