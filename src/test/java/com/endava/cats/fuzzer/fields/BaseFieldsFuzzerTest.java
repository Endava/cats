package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingResult;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.google.gson.JsonElement;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ExtendWith(SpringExtension.class)
class BaseFieldsFuzzerTest {

    @Mock
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;

    private BaseFieldsFuzzer baseFieldsFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @Test
    void givenAFieldWithAReplaceFuzzingStrategyWithANonPrimitiveField_whenTheFieldIsFuzzedAndNoExceptionOccurs_thenTheResultsAreRecordedCorrectly() {
        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Mockito.when(data.getAllFields()).thenReturn(fields);

        baseFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.eq("Field is not a primitive"));
    }

    @Test
    void givenAFieldWithASkipFuzzingStrategy_whenTheFieldIsFuzzedAndNoExceptionOccurs_thenTheResultsAreRecordedCorrectly() {
        baseFieldsFuzzer = new MyBaseFieldsSkipFuzzer(serviceCaller, testCaseListener, catsUtil);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Mockito.when(data.getAllFields()).thenReturn(fields);

        baseFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.eq(null));
    }

    @Test
    void givenAJSonPrimitiveFieldWithAReplaceFuzzingStrategy_whenTheFieldIsFuzzedAndNoExceptionOccurs_thenTheResultsAreRecordedCorrectly() {
        FuzzingResult fuzzingResult = Mockito.mock(FuzzingResult.class);
        Mockito.when(fuzzingResult.getJson()).thenReturn(Mockito.mock(JsonElement.class));
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Set<String> fields = Collections.singleton("field");
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("field", new StringSchema());
        Mockito.when(data.getAllFields()).thenReturn(fields);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        Mockito.when(catsUtil.isPrimitive(Mockito.eq(null), Mockito.anyString())).thenReturn(true);
        Mockito.when(catsUtil.replaceFieldWithFuzzedValue(Mockito.eq(null), Mockito.eq("field"), Mockito.any())).thenReturn(fuzzingResult);
        baseFieldsFuzzer = new MyBaseFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);

        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        baseFieldsFuzzer.fuzz(data);
        Mockito.verify(catsUtil).isPrimitive(Mockito.eq(null), Mockito.anyString());
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());
    }

    static class MyBaseFieldsFuzzer extends BaseFieldsFuzzer {

        public MyBaseFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
            super(sc, lr, cu);
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
        protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
            return FuzzingStrategy.replace();
        }

        @Override
        public String description() {
            return "cool description";
        }
    }

    static class MyBaseFieldsSkipFuzzer extends BaseFieldsFuzzer {

        public MyBaseFieldsSkipFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
            super(sc, lr, cu);
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
        protected FuzzingStrategy getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
            return FuzzingStrategy.skip();
        }

        @Override
        public String description() {
            return "cool description";
        }
    }


}
