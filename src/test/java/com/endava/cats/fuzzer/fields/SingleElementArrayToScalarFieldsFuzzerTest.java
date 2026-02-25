package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
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
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class SingleElementArrayToScalarFieldsFuzzerTest {
    private SingleElementArrayToScalarFieldsFuzzer fuzzer;
    private ServiceCaller serviceCaller;
    private ProcessingArguments processingArguments;
    @InjectSpy
    TestCaseListener testCaseListener;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(true);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new SingleElementArrayToScalarFieldsFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.description()).containsIgnoringCase("array");
    }

    @Test
    void shouldHaveTypes() {
        Assertions.assertThat(fuzzer.getOriginalType()).isEqualTo("primitive");
        Assertions.assertThat(fuzzer.getFuzzedType()).isEqualTo("single element array");
    }

    @Test
    void shouldReturnSingleElementArray() {
        Assertions.assertThat(fuzzer.getFuzzedValues("test")).containsExactly(List.of("test"));
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(fuzzer).hasToString(fuzzer.getClass().getSimpleName());
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "null"}, nullValues = "null")
    void shouldNotRunWithEmptyPayload(String payload) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn(payload);
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldNotRunWhenNoPrimitiveFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("tags", new ArraySchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("tags"));
        Mockito.when(data.getPayload()).thenReturn("{\"tags\": [\"tag1\", \"tag2\"]}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);

        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldRunWhenPrimitiveFieldPresent() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("name", new StringSchema());
        reqTypes.put("age", new IntegerSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name", "age"));
        Mockito.when(data.getPayload()).thenReturn("{\"name\": \"John\", \"age\": 25}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 1 array value for 2 primitive fields = 2 calls
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldRunForMultiplePrimitiveFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("name", new StringSchema());
        reqTypes.put("age", new IntegerSchema());
        reqTypes.put("count", new IntegerSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name", "age", "count"));
        Mockito.when(data.getPayload()).thenReturn("{\"name\": \"John\", \"age\": 25, \"count\": 10}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 1 array value for 3 primitive fields = 3 calls
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldSkipForHttpMethods() {
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsOnly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Test
    void shouldSkipFieldNotInPayload() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("name", new StringSchema());
        reqTypes.put("age", new IntegerSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name", "age"));
        Mockito.when(data.getPayload()).thenReturn("{\"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // Only name field should be fuzzed (1 array value)
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldHandleNestedPrimitiveFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("user#name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("user#name"));
        Mockito.when(data.getPayload()).thenReturn("{\"user\": {\"name\": \"John\"}}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 1 array value for 1 nested primitive field = 1 call
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect4xxWhenArrayCoercionNotAllowed() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new SingleElementArrayToScalarFieldsFuzzer(simpleExecutor, processingArguments);

        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name"));
        Mockito.when(data.getPayload()).thenReturn("{\"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

        fuzzer.fuzz(data);

        // 1 array value for 1 primitive field = 1 call, expecting 4XX
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect2xxWhenArrayCoercionAllowed() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new SingleElementArrayToScalarFieldsFuzzer(simpleExecutor, processingArguments);

        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name"));
        Mockito.when(data.getPayload()).thenReturn("{\"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 1 array value for 1 primitive field = 1 call, expecting 2XX
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }
}
