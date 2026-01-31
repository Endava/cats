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
import io.swagger.v3.oas.models.media.DateTimeSchema;
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
class EpochNumberInDateTimeFieldsFuzzerTest {
    private EpochNumberInDateTimeFieldsFuzzer fuzzer;
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
        fuzzer = new EpochNumberInDateTimeFieldsFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.description()).containsIgnoringCase("epoch");
    }

    @Test
    void shouldHaveTypes() {
        Assertions.assertThat(fuzzer.getOriginalType()).isEqualTo("date-time");
        Assertions.assertThat(fuzzer.getFuzzedType()).isEqualTo("epoch number");
    }

    @Test
    void shouldReturnEpochValues() {
        Assertions.assertThat(fuzzer.getFuzzedValues("2024-01-01T00:00:00Z")).hasSize(2);
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
    void shouldNotRunWhenNoDateTimeFields() {
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
    void shouldRunWhenDateTimeFieldPresent() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("createdAt", new DateTimeSchema());
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("createdAt", "name"));
        Mockito.when(data.getPayload()).thenReturn("{\"createdAt\": \"2024-01-01T00:00:00Z\", \"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 2 epoch values for 1 date-time field = 2 calls
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldRunForMultipleDateTimeFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("createdAt", new DateTimeSchema());
        reqTypes.put("updatedAt", new DateTimeSchema());
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("createdAt", "updatedAt", "name"));
        Mockito.when(data.getPayload()).thenReturn("{\"createdAt\": \"2024-01-01T00:00:00Z\", \"updatedAt\": \"2024-01-02T00:00:00Z\", \"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 2 epoch values for 2 date-time fields = 4 calls
        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(
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
        reqTypes.put("createdAt", new DateTimeSchema());
        reqTypes.put("updatedAt", new DateTimeSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("createdAt", "updatedAt"));
        Mockito.when(data.getPayload()).thenReturn("{\"createdAt\": \"2024-01-01T00:00:00Z\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // Only createdAt field should be fuzzed (2 epoch values)
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldHandleNestedDateTimeFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("metadata#createdAt", new DateTimeSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("metadata#createdAt"));
        Mockito.when(data.getPayload()).thenReturn("{\"metadata\": {\"createdAt\": \"2024-01-01T00:00:00Z\"}}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 2 epoch values for 1 nested date-time field = 2 calls
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect4xxWhenEpochCoercionNotAllowed() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new EpochNumberInDateTimeFieldsFuzzer(simpleExecutor, processingArguments);

        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("createdAt", new DateTimeSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("createdAt"));
        Mockito.when(data.getPayload()).thenReturn("{\"createdAt\": \"2024-01-01T00:00:00Z\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

        fuzzer.fuzz(data);

        // 2 epoch values for 1 date-time field = 2 calls, expecting 4XX
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect2xxWhenEpochCoercionAllowed() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new EpochNumberInDateTimeFieldsFuzzer(simpleExecutor, processingArguments);

        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("createdAt", new DateTimeSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("createdAt"));
        Mockito.when(data.getPayload()).thenReturn("{\"createdAt\": \"2024-01-01T00:00:00Z\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 2 epoch values for 1 date-time field = 2 calls, expecting 2XX
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }
}
