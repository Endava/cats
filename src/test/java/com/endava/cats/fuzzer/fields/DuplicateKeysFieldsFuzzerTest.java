package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class DuplicateKeysFieldsFuzzerTest {

    private SimpleExecutor simpleExecutor;
    private DuplicateKeysFieldsFuzzer duplicateKeysFieldsFuzzer;
    private FuzzingData data;
    private CatsResponse catsResponse;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        duplicateKeysFieldsFuzzer = new DuplicateKeysFieldsFuzzer(simpleExecutor);
    }

    @Test
    void shouldSkipForEmptyPayload() {
        FuzzingData emptyPayloadData = Mockito.mock(FuzzingData.class);
        Mockito.when(emptyPayloadData.getPayload()).thenReturn("");

        duplicateKeysFieldsFuzzer.fuzz(emptyPayloadData);

        Mockito.verify(simpleExecutor, Mockito.times(0)).execute(Mockito.any());
    }

    @Test
    void shouldSkipFieldExceedingDepthLimit() {
        setup(HttpMethod.POST);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("field#nested#too#deep"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(0)).execute(Mockito.any());
    }

    @Test
    void shouldSkipFieldNotInPayload() {
        setup(HttpMethod.POST);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("nonexistentField"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(0)).execute(Mockito.any());
    }

    @Test
    void shouldExecuteFieldDuplication() {
        setup(HttpMethod.POST);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("field"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldHandleErrorDuringPayloadDuplication() {
        setup(HttpMethod.POST);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Collections.singleton("field"));
        Mockito.when(data.getPayload()).thenReturn("invalidJson");

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(0)).execute(Mockito.any());
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

        Mockito.verify(simpleExecutor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldHandleMultipleFieldsWithLimit() {
        setup(HttpMethod.POST);
        Mockito.when(data.getPayload()).thenReturn("{\"field1\":\"value1\",\"field2\":\"value2\",\"field3\":\"value3\"}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("field1", "field2", "field3"));

        duplicateKeysFieldsFuzzer.fuzz(data);

        Mockito.verify(simpleExecutor, Mockito.times(3)).execute(Mockito.any());
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

    }
}
