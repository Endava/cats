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
import io.swagger.v3.oas.models.media.BooleanSchema;
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
import java.util.Map;
import java.util.Set;

@QuarkusTest
class BooleanStringInBooleanFieldsFuzzerTest {
    private BooleanStringInBooleanFieldsFuzzer fuzzer;
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
        fuzzer = new BooleanStringInBooleanFieldsFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.description()).containsIgnoringCase("boolean");
    }

    @Test
    void shouldHaveTypes() {
        Assertions.assertThat(fuzzer.getOriginalType()).isEqualTo("boolean");
        Assertions.assertThat(fuzzer.getFuzzedType()).isEqualTo("boolean string");
    }

    @Test
    void shouldReturnBooleanStringValue() {
        Assertions.assertThat(fuzzer.getFuzzedValues(true)).containsExactly("true");
        Assertions.assertThat(fuzzer.getFuzzedValues(false)).containsExactly("false");
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
    void shouldNotRunWhenNoBooleanFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("age", new IntegerSchema());
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("age", "name"));
        Mockito.when(data.getPayload()).thenReturn("{\"age\": 25, \"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);

        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldRunWhenBooleanFieldPresent() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("active", new BooleanSchema());
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("active", "name"));
        Mockito.when(data.getPayload()).thenReturn("{\"active\": true, \"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 1 string value for 1 boolean field = 1 call
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldRunForMultipleBooleanFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("active", new BooleanSchema());
        reqTypes.put("enabled", new BooleanSchema());
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("active", "enabled", "name"));
        Mockito.when(data.getPayload()).thenReturn("{\"active\": true, \"enabled\": false, \"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 1 string value for 2 boolean fields = 2 calls
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
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
        reqTypes.put("active", new BooleanSchema());
        reqTypes.put("enabled", new BooleanSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("active", "enabled"));
        Mockito.when(data.getPayload()).thenReturn("{\"active\": true}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // Only active field should be fuzzed (1 string value)
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldHandleNestedBooleanFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("user#active", new BooleanSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("user#active"));
        Mockito.when(data.getPayload()).thenReturn("{\"user\": {\"active\": true}}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 1 string value for 1 nested boolean field = 1 call
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect4xxWhenBooleanStringCoercionNotAllowed() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new BooleanStringInBooleanFieldsFuzzer(simpleExecutor, processingArguments);

        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("active", new BooleanSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("active"));
        Mockito.when(data.getPayload()).thenReturn("{\"active\": true}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

        fuzzer.fuzz(data);

        // 1 string value for 1 boolean field = 1 call, expecting 4XX
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect2xxWhenBooleanStringCoercionAllowed() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new BooleanStringInBooleanFieldsFuzzer(simpleExecutor, processingArguments);

        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("active", new BooleanSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("active"));
        Mockito.when(data.getPayload()).thenReturn("{\"active\": true}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 1 string value for 1 boolean field = 1 call, expecting 2XX
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }
}
