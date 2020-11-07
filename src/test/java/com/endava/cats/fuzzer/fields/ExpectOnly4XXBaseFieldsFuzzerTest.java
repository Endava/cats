package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class ExpectOnly4XXBaseFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private CatsParams catsParams;

    private ExpectOnly4XXBaseFieldsFuzzer expectOnly4XXBaseFieldsFuzzer;

    @BeforeEach
    void setup() {
        expectOnly4XXBaseFieldsFuzzer = new CustomExpect4XX(serviceCaller, testCaseListener, catsUtil, catsParams);
    }

    @Test
    void givenADummyExpectOnly4XXFieldsFuzzer_whenCreatingANewInstance_thenTheDefaultMethodsAreMatchingThe4XXFuzzer() {
        Assertions.assertThat(expectOnly4XXBaseFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(expectOnly4XXBaseFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(expectOnly4XXBaseFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(expectOnly4XXBaseFieldsFuzzer).hasToString(expectOnly4XXBaseFieldsFuzzer.getClass().getSimpleName());
    }

    static class CustomExpect4XX extends ExpectOnly4XXBaseFieldsFuzzer {

        public CustomExpect4XX(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, CatsParams cp) {
            super(sc, lr, cu, cp);
        }

        @Override
        protected String typeOfDataSentToTheService() {
            return null;
        }

        @Override
        protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
            return null;
        }

        @Override
        public String description() {
            return null;
        }
    }
}

