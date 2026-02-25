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
import io.swagger.v3.oas.models.media.NumberSchema;
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
class WhitespacePaddedNumericFieldsFuzzerTest {
    private WhitespacePaddedNumericFieldsFuzzer fuzzer;
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
        fuzzer = new WhitespacePaddedNumericFieldsFuzzer(simpleExecutor, processingArguments);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.description()).containsIgnoringCase("whitespace");
    }

    @Test
    void shouldHaveTypes() {
        Assertions.assertThat(fuzzer.getOriginalType()).isEqualTo("number");
        Assertions.assertThat(fuzzer.getFuzzedType()).isEqualTo("whitespace padded number");
    }

    @Test
    void shouldReturnWhitespacePaddedValues() {
        Assertions.assertThat(fuzzer.getFuzzedValues(123)).containsExactly(" 123", "\t123", "\n123", "\r123");
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
    void shouldNotRunWhenNoNumericFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("name", new StringSchema());
        reqTypes.put("description", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name", "description"));
        Mockito.when(data.getPayload()).thenReturn("{\"name\": \"John\", \"description\": \"test\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);

        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldRunWhenIntegerFieldPresent() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("age", new IntegerSchema());
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("age", "name"));
        Mockito.when(data.getPayload()).thenReturn("{\"age\": 25, \"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 4 whitespace variants for 1 integer field = 4 calls
        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldRunWhenNumberFieldPresent() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("price", new NumberSchema());
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("price", "name"));
        Mockito.when(data.getPayload()).thenReturn("{\"price\": 99.99, \"name\": \"Product\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 4 whitespace variants for 1 number field = 4 calls
        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldRunForMultipleNumericFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("age", new IntegerSchema());
        reqTypes.put("price", new NumberSchema());
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("age", "price", "name"));
        Mockito.when(data.getPayload()).thenReturn("{\"age\": 25, \"price\": 99.99, \"name\": \"John\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 4 whitespace variants for 2 numeric fields = 8 calls
        Mockito.verify(testCaseListener, Mockito.times(8)).reportResult(
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
        reqTypes.put("age", new IntegerSchema());
        reqTypes.put("count", new IntegerSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("age", "count"));
        Mockito.when(data.getPayload()).thenReturn("{\"age\": 25}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // Only age field should be fuzzed (4 whitespace variants)
        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldHandleNestedNumericFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("user#age", new IntegerSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("user#age"));
        Mockito.when(data.getPayload()).thenReturn("{\"user\": {\"age\": 30}}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 4 whitespace variants for 1 nested numeric field = 4 calls
        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect4xxWhenWhitespaceCoercionNotAllowed() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new WhitespacePaddedNumericFieldsFuzzer(simpleExecutor, processingArguments);

        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("age", new IntegerSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("age"));
        Mockito.when(data.getPayload()).thenReturn("{\"age\": 25}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

        fuzzer.fuzz(data);

        // 4 whitespace variants for 1 numeric field = 4 calls, expecting 4XX
        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect2xxWhenWhitespaceCoercionAllowed() {
        Mockito.when(processingArguments.isStrictTypes()).thenReturn(false);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new WhitespacePaddedNumericFieldsFuzzer(simpleExecutor, processingArguments);

        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("price", new NumberSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("price"));
        Mockito.when(data.getPayload()).thenReturn("{\"price\": 99.99}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        // 4 whitespace variants for 1 numeric field = 4 calls, expecting 2XX
        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }
}
