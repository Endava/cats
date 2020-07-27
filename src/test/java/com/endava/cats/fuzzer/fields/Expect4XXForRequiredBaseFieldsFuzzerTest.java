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
public class Expect4XXForRequiredBaseFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private Expect4XXForRequiredBaseFieldsFuzzer expect4XXForRequiredBaseFieldsFuzzer;

    @BeforeEach
    public void setup() {
        expect4XXForRequiredBaseFieldsFuzzer = new CustomExpect4XX(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenADummyExpect4XXForRequiredFieldsFuzzer_whenCreatingANewInstance_thenTheDefaultMethodsAreMatchingThe4XXFuzzer() {
        Assertions.assertThat(expect4XXForRequiredBaseFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(expect4XXForRequiredBaseFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(expect4XXForRequiredBaseFieldsFuzzer).hasToString(expect4XXForRequiredBaseFieldsFuzzer.getClass().getSimpleName());
    }

    class CustomExpect4XX extends Expect4XXForRequiredBaseFieldsFuzzer {

        public CustomExpect4XX(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
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
