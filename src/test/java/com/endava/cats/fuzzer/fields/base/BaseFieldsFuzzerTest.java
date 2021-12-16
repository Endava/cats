package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingResult;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
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
    @InjectSpy
    private CatsUtil catsUtil;
    private FilesArguments filesArguments;

    private BaseFieldsFuzzer baseFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void givenAFieldWithAReplaceFuzzingStrategyWithANonPrimitiveField_whenTheFieldIsFuzzedAndNoExceptionOccurs_thenTestsAreSkipped() {
        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Mockito.when(data.getAllFields()).thenReturn(fields);
        Mockito.when(data.getPayload()).thenReturn("{}");

        baseFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.eq("Field could not be fuzzed. Possible reasons: field is not a primitive, is a discriminator or is not matching the Fuzzer schemas"));
    }

    @Test
    void givenAFieldWithASkipFuzzingStrategy_whenTheFieldIsFuzzedAndNoExceptionOccurs_thenTestIsSkipped() {
        baseFieldsFuzzer = new MyBaseFieldsSkipFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Mockito.when(data.getAllFields()).thenReturn(fields);

        baseFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.eq(null));
    }

    @Test
    void givenAJSonPrimitiveFieldWithAReplaceFuzzingStrategy_whenTheFieldIsFuzzedAndNoExceptionOccurs_thenTestsAreExecuted() {
        FuzzingData data = createFuzzingData();

        baseFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());
    }

    @NotNull
    private FuzzingData createFuzzingData() {
        FuzzingResult fuzzingResult = Mockito.mock(FuzzingResult.class);
        Mockito.when(fuzzingResult.getJson()).thenReturn("{}");
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("field", new StringSchema());
        Mockito.when(data.getAllFields()).thenReturn(fields);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);
        Mockito.when(data.getPayload()).thenReturn("{\"field\": 2}");

        CatsUtil mockCatsUtil = Mockito.mock(CatsUtil.class);
        Mockito.when(mockCatsUtil.replaceField(Mockito.eq("{\"field\": 2}"), Mockito.eq("field"), Mockito.any())).thenReturn(fuzzingResult);
        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, mockCatsUtil, filesArguments);

        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());
        return data;
    }

    @Test
    void shouldNotRunWhenNoFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFields()).thenReturn(Collections.emptySet());
        CatsUtil mockCatsUtil = Mockito.mock(CatsUtil.class);
        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, mockCatsUtil, filesArguments);

        baseFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldSkipWhenFuzzingNotPossibleFromFuzzer() {
        FuzzingData data = createFuzzingData();
        BaseFieldsFuzzer spyFuzzer = Mockito.spy(baseFieldsFuzzer);
        Mockito.when(spyFuzzer.isFuzzingPossibleSpecificToFuzzer(Mockito.eq(data), Mockito.anyString(), Mockito.any())).thenReturn(false);
        spyFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.eq("Field could not be fuzzed. Possible reasons: field is not a primitive, is a discriminator or is not matching the Fuzzer schemas"));
    }

    @Test
    void shouldSkipWhenSkippedField() {
        FuzzingData data = createFuzzingData();
        BaseFieldsFuzzer spyFuzzer = Mockito.spy(baseFieldsFuzzer);
        Mockito.when(spyFuzzer.skipForFields()).thenReturn(List.of("field"));
        spyFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.eq("Field could not be fuzzed. Possible reasons: field is not a primitive, is a discriminator or is not matching the Fuzzer schemas"));
    }

    @ParameterizedTest
    @CsvSource(value = {"null,[a-z]+,200", "cats,[a-z]+,200", "CATS,[a-z]+,400"}, nullValues = "null")
    void shouldExpectDifferentCodesBasedOnFuzzedFieldMatchingPattern(String fuzzedValue, String pattern, String responseCode) {
        FuzzingResult fuzzingResult = new FuzzingResult("{\"field\":\"test\"}", fuzzedValue);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema schema = new StringSchema();
        schema.setPattern(pattern);
        schemaMap.put("field", schema);
        Mockito.when(data.getAllFields()).thenReturn(fields);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);
        Mockito.when(data.getPayload()).thenReturn("{\"field\": 2}");

        CatsUtil mockCatsUtil = Mockito.mock(CatsUtil.class);
        Mockito.when(mockCatsUtil.replaceField(Mockito.eq("{\"field\": 2}"), Mockito.eq("field"), Mockito.any())).thenReturn(fuzzingResult);
        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, mockCatsUtil, filesArguments);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        baseFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.from(responseCode)));
    }

    static class MyBaseFieldsFuzzer extends BaseFieldsFuzzer {

        public MyBaseFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
            super(sc, lr, cu, cp);
        }

        @Override
        protected String typeOfDataSentToTheService() {
            return "test data";
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
            return ResponseCodeFamily.FOURXX;
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
            return ResponseCodeFamily.TWOXX;
        }

        @Override
        protected ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
            return ResponseCodeFamily.FOURXX;
        }

        @Override
        protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
            return Collections.singletonList(FuzzingStrategy.replace());
        }

        @Override
        public String description() {
            return "cool description";
        }
    }

    static class MyBaseFieldsSkipFuzzer extends BaseFieldsFuzzer {

        public MyBaseFieldsSkipFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
            super(sc, lr, cu, cp);
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
            return Collections.singletonList(FuzzingStrategy.skip());
        }

        @Override
        public String description() {
            return "cool description";
        }
    }


}
