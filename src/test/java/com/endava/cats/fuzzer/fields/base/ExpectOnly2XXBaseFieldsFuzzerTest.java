package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

@QuarkusTest
class ExpectOnly2XXBaseFieldsFuzzerTest {

    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private CatsUtil catsUtil;
    private FilesArguments filesArguments;

    private ExpectOnly2XXBaseFieldsFuzzer expectOnly2XXBaseFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        catsUtil = Mockito.mock(CatsUtil.class);
        filesArguments = Mockito.mock(FilesArguments.class);
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
