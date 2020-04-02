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
public class ExpectOnly2XXBaseFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private ExpectOnly2XXBaseFieldsFuzzer expectOnly2XXBaseFieldsFuzzer;

    @BeforeEach
    public void setup() {
        expectOnly2XXBaseFieldsFuzzer = new ExpectOnly2XXBaseFieldsFuzzerTest.CustomExpect2XX(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenADummyExpectOnly2XXFieldsFuzzer_whenCreatingANewInstance_thenTheDefaultMethodsAreMatchingThe2XXFuzzer() {
        Assertions.assertThat(expectOnly2XXBaseFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(expectOnly2XXBaseFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(expectOnly2XXBaseFieldsFuzzer.getClass().getSimpleName()).isEqualTo(expectOnly2XXBaseFieldsFuzzer.toString());
    }

    class CustomExpect2XX extends ExpectOnly2XXBaseFieldsFuzzer {

        public CustomExpect2XX(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
            super(sc, lr, cu);
        }

        @Override
        protected String typeOfDataSentToTheService() {
            return null;
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
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
