package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class NullElementsInArrayFieldsFuzzerTest {
    ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    FieldsIteratorExecutor catsExecutor;
    private NullElementsInArrayFieldsFuzzer nullElementsInArrayFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        catsExecutor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        nullElementsInArrayFieldsFuzzer = new NullElementsInArrayFieldsFuzzer(catsExecutor);
    }

    @Test
    void shouldRunIfFieldArrayAndSkipIfPrimitive() {
        Assertions.assertThat(nullElementsInArrayFieldsFuzzer.description()).isNotBlank();
        Assertions.assertThat(nullElementsInArrayFieldsFuzzer).hasToString("NullElementsInArrayFieldsFuzzer");
        Assertions.assertThat(nullElementsInArrayFieldsFuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("arrayField"));
        Mockito.when(data.getPayload()).thenReturn("{\"arrayField\":[{\"inner\":\"innerValue\"}]}");

        nullElementsInArrayFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));

        // When array items are nullable, [null] is valid and must be skipped
        ArraySchema nullableArray = new ArraySchema();
        StringSchema nullableItem = new StringSchema();
        nullableItem.setNullable(true);
        nullableArray.setItems(nullableItem);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("arrayField", nullableArray));
        nullElementsInArrayFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));

        // OpenAPI 3.1 types containing "null" should also be skipped
        ArraySchema oas31NullableArray = new ArraySchema();
        Schema<?> oas31Item = new Schema<>();
        oas31Item.setTypes(Set.of("string", "null"));
        oas31NullableArray.setItems(oas31Item);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("arrayField", oas31NullableArray));
        nullElementsInArrayFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));

        // Non-nullable OpenAPI 3.1 types and array without items should run
        ArraySchema nonNullableOas31Array = new ArraySchema();
        Schema<?> nonNullItem = new Schema<>();
        nonNullItem.setTypes(Set.of("string"));
        nonNullableOas31Array.setItems(nonNullItem);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("arrayField", nonNullableOas31Array));
        nullElementsInArrayFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(2))
                .reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }
}
