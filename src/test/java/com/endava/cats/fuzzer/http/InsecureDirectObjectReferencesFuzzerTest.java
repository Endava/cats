package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
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
class InsecureDirectObjectReferencesFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private SimpleExecutor simpleExecutor;
    private InsecureDirectObjectReferencesFuzzer idorFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        idorFuzzer = new InsecureDirectObjectReferencesFuzzer(simpleExecutor, testCaseListener);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    @Test
    void shouldSkipWhenNoIdFieldsFound() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("name", new StringSchema());
        reqTypes.put("description", new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .method(HttpMethod.GET)
                .build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"name\": \"test\", \"description\": \"desc\"}");

        idorFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldFuzzWhenIdFieldFound() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("userId", new StringSchema());
        reqTypes.put("name", new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200", "403"))
                .method(HttpMethod.GET)
                .build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"userId\": \"123\", \"name\": \"test\"}");

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(403).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        idorFuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.atLeastOnce()).call(Mockito.any());
    }

    @Test
    void shouldFuzzUuidField() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("resourceId", new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200", "403"))
                .method(HttpMethod.GET)
                .build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"resourceId\": \"550e8400-e29b-41d4-a716-446655440000\"}");

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(404).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        idorFuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.atLeastOnce()).call(Mockito.any());
    }

    @Test
    void shouldReportErrorWhenSuccessResponseWithModifiedId() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("userId", new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200"))
                .method(HttpMethod.GET)
                .build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"userId\": \"123\"}");

        CatsResponse catsResponse = CatsResponse.builder().body("{\"data\": \"sensitive\"}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        idorFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                Mockito.any(), Mockito.eq(data), Mockito.contains("IDOR"), Mockito.anyString(), Mockito.any());
    }

    @ParameterizedTest
    @CsvSource({
            "userId, true",
            "user_id, true",
            "accountId, true",
            "account_id, true",
            "id, true",
            "uuid, true",
            "guid, true",
            "orderId, true",
            "order-id, true",
            "customerId, true",
            "name, false",
            "description, false",
            "email, false",
            "identifier, false"
    })
    void shouldIdentifyIdFields(String fieldName, boolean shouldBeId) {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put(fieldName, new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200"))
                .method(HttpMethod.GET)
                .build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"" + fieldName + "\": \"123\"}");

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(403).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        idorFuzzer.fuzz(data);

        if (shouldBeId) {
            Mockito.verify(serviceCaller, Mockito.atLeastOnce()).call(Mockito.any());
        } else {
            Mockito.verifyNoInteractions(serviceCaller);
        }
    }

    @Test
    void shouldSkipForHeadAndTrace() {
        Assertions.assertThat(idorFuzzer.skipForHttpMethods())
                .containsExactlyInAnyOrder(HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(idorFuzzer.description()).isNotBlank();
        Assertions.assertThat(idorFuzzer.description()).containsIgnoringCase("IDOR");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(idorFuzzer).hasToString(idorFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldFuzzNestedIdField() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("user#accountId", new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200"))
                .method(HttpMethod.GET)
                .build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"user\": {\"accountId\": \"456\"}}");

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(403).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        idorFuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.atLeastOnce()).call(Mockito.any());
    }

    @Test
    void shouldSkipFieldWithNoValueSet() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("userId", new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200"))
                .method(HttpMethod.GET)
                .build();
        // Field exists in schema but not in payload - getVariableFromJson returns NOT_SET
        ReflectionTestUtils.setField(data, "processedPayload", "{\"name\": \"test\"}");

        idorFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldReportWarnForUnexpectedResponseCode() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("userId", new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200"))
                .method(HttpMethod.GET)
                .build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"userId\": \"123\"}");

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(302).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        idorFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultWarn(
                Mockito.any(), Mockito.eq(data), Mockito.contains("Unexpected"), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldReportInfoFor4xxResponse() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("userId", new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200"))
                .method(HttpMethod.GET)
                .build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"userId\": \"123\"}");

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(403).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        idorFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldGenerateAlternativesForStringBasedIds() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("userId", new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200"))
                .method(HttpMethod.GET)
                .build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"userId\": \"john_doe\"}");

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(403).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        idorFuzzer.fuzz(data);

        // String-based IDs generate 4 alternatives
        Mockito.verify(serviceCaller, Mockito.times(4)).call(Mockito.any());
    }

    @Test
    void shouldGenerateAlternativesForNumericIds() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("userId", new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .reqSchema(new StringSchema())
                .requestPropertyTypes(reqTypes)
                .requestContentTypes(List.of("application/json"))
                .responseCodes(Set.of("200"))
                .method(HttpMethod.GET)
                .build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"userId\": \"12345\"}");

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(403).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        idorFuzzer.fuzz(data);

        // Numeric IDs generate 5 alternatives
        Mockito.verify(serviceCaller, Mockito.times(5)).call(Mockito.any());
    }
}
