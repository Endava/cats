package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class ExpectOnly4XXBaseFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private ExpectOnly4XXBaseFieldsFuzzer expectOnly4XXBaseFieldsFuzzer;

    @BeforeEach
    public void setup() {
        expectOnly4XXBaseFieldsFuzzer = new ExpectOnly4XXBaseFieldsFuzzerTest.CustomExpect4XX(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenADummyExpectOnly4XXFieldsFuzzer_whenCreatingANewInstance_thenTheDefaultMethodsAreMatchingThe4XXFuzzer() {
        Assertions.assertThat(expectOnly4XXBaseFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(expectOnly4XXBaseFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(expectOnly4XXBaseFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(expectOnly4XXBaseFieldsFuzzer).hasToString(expectOnly4XXBaseFieldsFuzzer.getClass().getSimpleName());
    }

    class CustomExpect4XX extends ExpectOnly4XXBaseFieldsFuzzer {

        public CustomExpect4XX(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
            super(sc, lr, cu);
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

