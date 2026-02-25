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
class EnumNumericOrdinalFieldsFuzzerTest {
    private EnumNumericOrdinalFieldsFuzzer fuzzer;
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
        fuzzer = new EnumNumericOrdinalFieldsFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.description()).containsIgnoringCase("enum");
    }

    @Test
    void shouldHaveTypes() {
        Assertions.assertThat(fuzzer.getOriginalType()).isEqualTo("enum");
        Assertions.assertThat(fuzzer.getFuzzedType()).isEqualTo("numeric enum ordinal");
    }

    @Test
    void shouldReturnNumericOrdinalValues() {
        Assertions.assertThat(fuzzer.getFuzzedValues("RED")).containsExactly(0, 1);
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
    void shouldNotRunWhenNoEnumFields() {
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
    void shouldRunWhenEnumFieldPresent() {
        Map<String, Schema> reqTypes = new HashMap<>();
        StringSchema enumSchema = new StringSchema();
        enumSchema.setEnum(List.of("RED", "GREEN", "BLUE"));
        reqTypes.put("color", enumSchema);
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("color", "name"));
        Mockito.when(data.getPayload()).thenReturn("{\"color\": \"RED\", \"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 2 ordinal values (0, 1) for 1 enum field = 2 calls
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldRunForMultipleEnumFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        StringSchema colorSchema = new StringSchema();
        colorSchema.setEnum(List.of("RED", "GREEN", "BLUE"));
        StringSchema sizeSchema = new StringSchema();
        sizeSchema.setEnum(List.of("SMALL", "MEDIUM", "LARGE"));
        reqTypes.put("color", colorSchema);
        reqTypes.put("size", sizeSchema);
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("color", "size", "name"));
        Mockito.when(data.getPayload()).thenReturn("{\"color\": \"RED\", \"size\": \"MEDIUM\", \"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 2 ordinal values for 2 enum fields = 4 calls
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
        StringSchema colorSchema = new StringSchema();
        colorSchema.setEnum(List.of("RED", "GREEN", "BLUE"));
        StringSchema sizeSchema = new StringSchema();
        sizeSchema.setEnum(List.of("SMALL", "MEDIUM", "LARGE"));
        reqTypes.put("color", colorSchema);
        reqTypes.put("size", sizeSchema);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("color", "size"));
        Mockito.when(data.getPayload()).thenReturn("{\"color\": \"RED\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // Only color field should be fuzzed (2 ordinal values)
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldHandleNestedEnumFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        StringSchema enumSchema = new StringSchema();
        enumSchema.setEnum(List.of("RED", "GREEN", "BLUE"));
        reqTypes.put("product#color", enumSchema);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("product#color"));
        Mockito.when(data.getPayload()).thenReturn("{\"product\": {\"color\": \"RED\"}}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 2 ordinal values for 1 nested enum field = 2 calls
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect4xxWhenEnumOrdinalCoercionNotAllowed() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new EnumNumericOrdinalFieldsFuzzer(simpleExecutor, processingArguments);

        Map<String, Schema> reqTypes = new HashMap<>();
        StringSchema enumSchema = new StringSchema();
        enumSchema.setEnum(List.of("RED", "GREEN", "BLUE"));
        reqTypes.put("color", enumSchema);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("color"));
        Mockito.when(data.getPayload()).thenReturn("{\"color\": \"RED\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

        fuzzer.fuzz(data);

        // 2 ordinal values for 1 enum field = 2 calls, expecting 4XX
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect2xxWhenEnumOrdinalCoercionAllowed() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new EnumNumericOrdinalFieldsFuzzer(simpleExecutor, processingArguments);

        Map<String, Schema> reqTypes = new HashMap<>();
        StringSchema enumSchema = new StringSchema();
        enumSchema.setEnum(List.of("RED", "GREEN", "BLUE"));
        reqTypes.put("color", enumSchema);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("color"));
        Mockito.when(data.getPayload()).thenReturn("{\"color\": \"RED\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 2 ordinal values for 1 enum field = 2 calls, expecting 2XX
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }
}
