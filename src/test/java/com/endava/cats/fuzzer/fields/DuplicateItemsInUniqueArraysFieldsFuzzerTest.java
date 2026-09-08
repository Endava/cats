package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class DuplicateItemsInUniqueArraysFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private DuplicateItemsInUniqueArraysFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        fuzzer = new DuplicateItemsInUniqueArraysFieldsFuzzer(new SimpleExecutor(testCaseListener, serviceCaller));
    }

    @Test
    void shouldDuplicateAnItemAndPreserveArraySize() {
        FuzzingData data = fuzzingData("""
                {"items":[{"id":1},{"id":2},{"id":3}]}
                """, uniqueArraySchema(null));
        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().body("{}").responseCode(400).build());

        fuzzer.fuzz(data);

        ArgumentCaptor<ServiceData> request = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller).call(request.capture());
        String payload = request.getValue().getPayload();
        var items = JsonUtils.parseAsJsonElement(payload).getAsJsonObject().getAsJsonArray("items");
        Assertions.assertThat(items).hasSize(3);
        Assertions.assertThat(items.get(0)).isEqualTo(items.get(2));
        Assertions.assertThat(items.get(1)).isNotEqualTo(items.get(0));
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.eq(true), Mockito.eq(true));
    }

    @Test
    void shouldAppendTheDuplicateForASingleItemWhenMaxItemsAllowsIt() {
        FuzzingData data = fuzzingData("{\"items\":[\"one\"]}", uniqueArraySchema(2));
        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().body("{}").responseCode(400).build());

        fuzzer.fuzz(data);

        ArgumentCaptor<ServiceData> request = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller).call(request.capture());
        Assertions.assertThat(JsonUtils.equalAsJson(request.getValue().getPayload(),
                "{\"items\":[\"one\",\"one\"]}")).isTrue();
    }

    @Test
    void shouldDuplicateAnItemInANestedArray() {
        FuzzingData data = fuzzingData("""
                {"container":{"items":[1,2]}}
                """, "container#items", uniqueArraySchema(null));
        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().body("{}").responseCode(400).build());

        fuzzer.fuzz(data);

        ArgumentCaptor<ServiceData> request = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller).call(request.capture());
        Assertions.assertThat(JsonUtils.equalAsJson(request.getValue().getPayload(),
                "{\"container\":{\"items\":[1,1]}}")).isTrue();
    }

    @Test
    void shouldDuplicateAnItemInAReferencedRootArray() {
        ArraySchema schema = uniqueArraySchema(null);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("[1,2]");
        Mockito.when(data.getReqSchema()).thenReturn(new Schema<>().$ref("#/components/schemas/UniqueArray"));
        Mockito.when(data.getSchemaMap()).thenReturn(Map.of("UniqueArray", schema));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");
        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().body("{}").responseCode(400).build());

        fuzzer.fuzz(data);

        ArgumentCaptor<ServiceData> request = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller).call(request.capture());
        Assertions.assertThat(JsonUtils.equalAsJson(request.getValue().getPayload(), "[1,1]")).isTrue();
    }

    @Test
    void shouldSkipArraysNotMarkedAsUnique() {
        ArraySchema schema = new ArraySchema();
        schema.setUniqueItems(false);
        FuzzingData data = fuzzingData("{\"items\":[1,2]}", schema);

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldSkipEmptyArraysAndSingleItemArraysWithMaxItemsOne() {
        FuzzingData emptyArray = fuzzingData("{\"items\":[]}", uniqueArraySchema(null));
        FuzzingData constrainedArray = fuzzingData("{\"items\":[1]}", uniqueArraySchema(1));

        fuzzer.fuzz(emptyArray);
        fuzzer.fuzz(constrainedArray);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldExposeNameDescriptionAndSkippedMethods() {
        Assertions.assertThat(fuzzer).hasToString("DuplicateItemsInUniqueArraysFieldsFuzzer");
        Assertions.assertThat(fuzzer.description()).contains("uniqueItems", "duplicate");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    private FuzzingData fuzzingData(String payload, ArraySchema schema) {
        return fuzzingData(payload, "items", schema);
    }

    private FuzzingData fuzzingData(String payload, String field, ArraySchema schema) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of(field));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of(field, schema));
        Mockito.when(data.getPayload()).thenReturn(payload);
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");
        return data;
    }

    private ArraySchema uniqueArraySchema(Integer maxItems) {
        ArraySchema schema = new ArraySchema();
        schema.setUniqueItems(true);
        schema.setMaxItems(maxItems);
        return schema;
    }
}
