package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
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
class DuplicateKeysFieldsFuzzerTest {

    @InjectSpy
    private TestCaseListener testCaseListener;
    private ServiceCaller serviceCaller;
    private DuplicateKeysFieldsFuzzer duplicateKeysFieldsFuzzer;
    private FuzzingData data;
    private CatsResponse catsResponse;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        duplicateKeysFieldsFuzzer = new DuplicateKeysFieldsFuzzer(serviceCaller, testCaseListener);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    @Test
    void shouldSkipForEmptyPayload() {
        FuzzingData emptyPayloadData = Mockito.mock(FuzzingData.class);
        Mockito.when(emptyPayloadData.getPayload()).thenReturn("");

        duplicateKeysFieldsFuzzer.fuzz(emptyPayloadData);

        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldSkipFieldExceedingDepthLimit() {
        setup(HttpMethod.POST);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("field#nested#too#deep"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldSkipFieldNotInPayload() {
        setup(HttpMethod.POST);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("nonexistentField"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldExecuteFieldDuplication() {
        setup(HttpMethod.POST);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("field"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldHandleErrorDuringPayloadDuplication() {
        setup(HttpMethod.POST);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("field"));
        Mockito.when(data.getPayload()).thenReturn("invalidJson");

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @ParameterizedTest
    @CsvSource(value = {"{\"parent\":{\"child\":\"value\"}}|parent#child",
            "{\"items\":[{\"field\":\"value1\"},{\"field\":\"value2\"}]}|items",
            "{\"numbers\":[1,2,3]}|numbers",
            "{\"level1\":{\"level2\":{\"level3\":\"value\"}}}|level1#level2#level3",
            "{\"items\":[]}|items",
            "{\"data\":{\"items\":[{\"id\":1},{\"id\":2}]}}|data#items",
            "{\"field-with-dash\":\"value\"}|field-with-dash"}, delimiter = '|')
    void shouldHandleNestedObjectDuplication(String payload, String field) {
        setup(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn(payload);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton(field));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldHandleMultipleFieldsWithLimit() {
        setup(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"field1\":\"value1\",\"field2\":\"value2\",\"field3\":\"value3\"}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("field1", "field2", "field3"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldSkipTestWhen2xxFilterEnabledAndExpects4xx() {
        FilterArguments filterArguments = Mockito.mock(FilterArguments.class);
        Mockito.when(filterArguments.isOnly4xxFuzzers()).thenReturn(false);
        Mockito.when(filterArguments.isOnly2xxFuzzers()).thenReturn(true);
        ReflectionTestUtils.setField(testCaseListener, "filterArguments", filterArguments);
        
        setup(HttpMethod.POST);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("field"));
        duplicateKeysFieldsFuzzer.fuzz(data);
        
        Mockito.verify(testCaseListener).skipTest(Mockito.any(PrettyLogger.class), Mockito.eq("Test skipped due to response code filtering"));
        Mockito.verify(serviceCaller, Mockito.never()).call(Mockito.any());
    }

    private void setup(HttpMethod method) {
        catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPath()).thenReturn("path1");
        Mockito.when(data.getMethod()).thenReturn(method);
        Mockito.when(data.getPayload()).thenReturn("{\"field\":\"value\"}");
        Mockito.when(data.getResponses()).thenReturn(responses);
        Mockito.when(data.getResponseCodes()).thenReturn(Collections.singleton("200"));

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
    }
}
