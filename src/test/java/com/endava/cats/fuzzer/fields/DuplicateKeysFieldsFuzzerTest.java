package com.endava.cats.fuzzer.fields;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @Test
    void shouldHandleNestedObjectDuplication() {
        setup(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"parent\":{\"child\":\"value\"}}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("parent#child"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldHandleArrayWithDuplicateKey() {
        setup(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"items\":[{\"field\":\"value1\"},{\"field\":\"value2\"}]}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("items"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldHandleArrayOfPrimitives() {
        setup(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"numbers\":[1,2,3]}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("numbers"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldHandleDeepNestedStructure() {
        setup(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"level1\":{\"level2\":{\"level3\":\"value\"}}}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("level1#level2#level3"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldHandleEmptyArray() {
        setup(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"items\":[]}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("items"));

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
    void shouldHandleComplexNestedArrays() {
        setup(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"data\":{\"items\":[{\"id\":1},{\"id\":2}]}}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("data#items"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldHandleSpecialCharactersInKeys() {
        setup(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"field-with-dash\":\"value\"}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("field-with-dash"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
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
