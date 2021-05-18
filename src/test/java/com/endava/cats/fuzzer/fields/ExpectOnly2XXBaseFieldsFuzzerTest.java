package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.fields.base.ExpectOnly2XXBaseFieldsFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

@ExtendWith(SpringExtension.class)
class ExpectOnly2XXBaseFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private ExpectOnly2XXBaseFieldsFuzzer expectOnly2XXBaseFieldsFuzzer;

    @BeforeEach
    void setup() {
        expectOnly2XXBaseFieldsFuzzer = new CustomExpect2XX(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenADummyExpectOnly2XXFieldsFuzzer_whenCreatingANewInstance_thenTheDefaultMethodsAreMatchingThe2XXFuzzer() {
        Assertions.assertThat(expectOnly2XXBaseFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(expectOnly2XXBaseFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(expectOnly2XXBaseFieldsFuzzer).hasToString(expectOnly2XXBaseFieldsFuzzer.getClass().getSimpleName());
    }

    static class CustomExpect2XX extends ExpectOnly2XXBaseFieldsFuzzer {

        public CustomExpect2XX(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
            super(sc, lr, cu, cp);
        }

        @Override
        protected String typeOfDataSentToTheService() {
            return null;
        }

        @Override
        public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
            return null;
        }

        @Override
        protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
            return null;
        }

        @Override
        public String description() {
            return null;
        }
    }
}
