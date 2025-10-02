package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.strategy.FuzzingStrategy;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class BaseFieldsFuzzerTest {

    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;

    private BaseFieldsFuzzer baseFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).body("{}").build());
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    @Test
    void givenAFieldWithAReplaceFuzzingStrategyWithANonPrimitiveField_whenTheFieldIsFuzzedAndNoExceptionOccurs_thenTestsAreSkipped() {
        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, filesArguments, "");
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(fields);
        Mockito.when(data.getPayload()).thenReturn("{}");

        baseFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.eq("field could not be fuzzed. Possible reasons: field is not a primitive, is a discriminator, is passed as refData or is not matching the Fuzzer schemas"));
    }

    @Test
    void shouldSkipFuzzerWhenSkipStrategy() {
        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, filesArguments, "");
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(fields);
        Mockito.when(data.getPayload()).thenReturn("{}");
        testCaseListener.createAndExecuteTest(Mockito.mock(PrettyLogger.class), Mockito.mock(Fuzzer.class), () -> baseFieldsFuzzer.process(data, "field", FuzzingStrategy.skip().withData("Skipping test")), data);
        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.eq("Skipping test"));
    }

    @Test
    void givenAFieldWithASkipFuzzingStrategy_whenTheFieldIsFuzzedAndNoExceptionOccurs_thenTestIsNotRun() {
        baseFieldsFuzzer = new MyBaseFieldsSkipFuzzer(serviceCaller, testCaseListener, filesArguments);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(fields);

        baseFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void givenAJSonPrimitiveFieldWithAReplaceFuzzingStrategy_whenTheFieldIsFuzzedAndNoExceptionOccurs_thenTestsAreExecuted() {
        FuzzingData data = createFuzzingData();

        baseFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.eq(true));
    }

    @NotNull
    private FuzzingData createFuzzingData() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("field", new StringSchema());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(fields);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);
        Mockito.when(data.getPayload()).thenReturn("{\"field\": 2}");

        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, filesArguments, "");

        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());
        return data;
    }

    @Test
    void shouldNotRunWhenNoFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.emptySet());
        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, filesArguments, "");

        baseFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldSkipWhenFuzzingNotPossibleFromFuzzer() {
        FuzzingData data = createFuzzingData();
        BaseFieldsFuzzer spyFuzzer = Mockito.spy(baseFieldsFuzzer);
        Mockito.when(spyFuzzer.isFuzzerWillingToFuzz(Mockito.eq(data), Mockito.anyString())).thenReturn(false);
        spyFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.eq("field could not be fuzzed. Possible reasons: field is not a primitive, is a discriminator, is passed as refData or is not matching the Fuzzer schemas"));
    }

    @Test
    void shouldSkipWhenSkippedField() {
        FuzzingData data = createFuzzingData();
        BaseFieldsFuzzer spyFuzzer = Mockito.spy(baseFieldsFuzzer);
        Mockito.when(spyFuzzer.skipForFields()).thenReturn(List.of("field"));
        spyFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.eq("field could not be fuzzed. Possible reasons: field is not a primitive, is a discriminator, is passed as refData or is not matching the Fuzzer schemas"));
    }

    @ParameterizedTest
    @CsvSource(value = {"null,[a-z]+,200", "cats,[a-z]+,200", "CATS,[a-z]+,400"}, nullValues = "null")
    void shouldExpectDifferentCodesBasedOnFuzzedFieldMatchingPattern(String fuzzedValue, String pattern, String responseCode) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema schema = new StringSchema();
        schema.setPattern(pattern);
        schemaMap.put("field", schema);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(fields);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);
        Mockito.when(data.getPayload()).thenReturn("{\"field\": 2}");

        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, filesArguments, fuzzedValue);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        baseFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.from(responseCode)), Mockito.eq(true), Mockito.eq(true));
    }

    static class MyBaseFieldsFuzzer extends BaseFieldsFuzzer {
        private final String fuzzedValue;

        public MyBaseFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, String fuzzedValue) {
            super(sc, lr, cp);
            this.fuzzedValue = fuzzedValue;
        }

        @Override
        protected String typeOfDataSentToTheService() {
            return "test data";
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
            return ResponseCodeFamilyPredefined.FOURXX;
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
            return ResponseCodeFamilyPredefined.TWOXX;
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
            return ResponseCodeFamilyPredefined.FOURXX;
        }

        @Override
        protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
            return Collections.singletonList(FuzzingStrategy.replace().withData(fuzzedValue));
        }

        @Override
        public String description() {
            return "cool description";
        }
    }

    static class MyBaseFieldsSkipFuzzer extends BaseFieldsFuzzer {

        public MyBaseFieldsSkipFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp) {
            super(sc, lr, cp);
        }

        @Override
        protected String typeOfDataSentToTheService() {
            return "test data";
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
            return null;
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
            return null;
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
            return null;
        }

        @Override
        protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
            return Collections.singletonList(FuzzingStrategy.skip().withData(""));
        }

        @Override
        public String description() {
            return "cool description";
        }
    }


}
