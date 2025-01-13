package com.endava.cats.fuzzer.fields;

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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@QuarkusTest
class MassAssignmentFuzzerTest {

    private ServiceCaller caller;
    @InjectSpy
    TestCaseListener listener;
    private SimpleExecutor executor;
    private MassAssignmentFuzzer fuzzer;

    @BeforeEach
    void setup() {
        caller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(listener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        executor = new SimpleExecutor(listener, caller);
        fuzzer = new MassAssignmentFuzzer(executor, listener);
    }

    @Test
    void shouldHaveCorrectDescription() {
        Assertions.assertThat(fuzzer.description()).containsIgnoringCase("Mass Assignment");
    }

    @Test
    void shouldHaveCorrectToString() {
        Assertions.assertThat(fuzzer).hasToString("MassAssignmentFuzzer");
    }

    @Test
    void shouldSkipCorrectHttpMethods() {
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsOnly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "null"}, nullValues = "null")
    void shouldSkipEmptyPayload(String payloadValue) {
        FuzzingData fuzzData = Mockito.mock(FuzzingData.class);
        Mockito.when(fuzzData.getPayload()).thenReturn(payloadValue);
        fuzzer.fuzz(fuzzData);
        Mockito.verifyNoInteractions(caller);
    }

    @Test
    void shouldSkipWhenAllFieldsInSchema() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("role", new StringSchema());
        schemaMap.put("isAdmin", new StringSchema());

        FuzzingData fuzzData = Mockito.mock(FuzzingData.class);
        Set<String> fieldsSet = Set.of("role", "isAdmin", "admin", "roles",
                "is_admin", "permissions", "privilege", "privileges", "accessLevel", "access_level",
                "userType", "user_type", "accountType", "verified", "emailVerified", "email_verified",
                "approved", "active", "enabled", "status", "accountStatus", "banned", "suspended",
                "balance", "credits", "points", "price", "discount", "discountPercent", "amount", "fee",
                "userId", "user_id", "ownerId", "owner_id", "createdBy", "created_by", "organizationId",
                "tenantId", "internal", "isInternal", "debug", "test");
        Mockito.when(fuzzData.getAllFieldsByHttpMethod()).thenReturn(fieldsSet);
        Mockito.when(fuzzData.getPayload()).thenReturn("{\"role\": \"user\"}");
        Mockito.when(fuzzData.getRequestPropertyTypes()).thenReturn(schemaMap);

        fuzzer.fuzz(fuzzData);
        Mockito.verifyNoInteractions(caller);
    }

    @Test
    void shouldFuzzWhenUndeclaredFieldsExist() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("username", new StringSchema());

        FuzzingData fuzzData = Mockito.mock(FuzzingData.class);
        Mockito.when(fuzzData.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
        Mockito.when(fuzzData.getPayload()).thenReturn("{\"username\": \"test\"}");
        Mockito.when(fuzzData.getRequestPropertyTypes()).thenReturn(schemaMap);
        Mockito.when(fuzzData.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(fuzzData.getHeaders()).thenReturn(Set.of());
        Mockito.when(fuzzData.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(caller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

        fuzzer.fuzz(fuzzData);

        Mockito.verify(caller, Mockito.atLeast(1)).call(Mockito.any());
    }

    static Stream<Arguments> provideTestScenarios() {
        return Stream.of(
                Arguments.of("Field reflected with admin role", 200,
                        "{\"username\": \"test\", \"role\": \"admin\"}",
                        "Mass Assignment vulnerability detected", true),
                Arguments.of("Field accepted no reflection", 200,
                        "{\"username\": \"test\"}",
                        "Undeclared field accepted", true),
                Arguments.of("Request rejected 4xx", 400,
                        "Bad Request",
                        "Mass Assignment payload rejected", false),
                Arguments.of("Server error 5xx", 500,
                        "Internal Server Error",
                        "Server error", true),
                Arguments.of("Redirect response 3xx", 301,
                        "Moved Permanently",
                        "Unexpected response code", true),
                Arguments.of("isAdmin flag reflected", 200,
                        "{\"username\": \"test\", \"isAdmin\": true}",
                        "Mass Assignment vulnerability detected", true),
                Arguments.of("admin flag reflected", 200,
                        "{\"username\": \"test\", \"admin\": true}",
                        "Mass Assignment vulnerability detected", true),
                Arguments.of("balance field reflected", 200,
                        "{\"username\": \"test\", \"balance\": 999999}",
                        "Mass Assignment vulnerability detected", true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTestScenarios")
    void shouldHandleMassAssignmentResponses(String scenarioName, int code, String body,
                                             String expectedMsg, boolean shouldError) {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("username", new StringSchema());

        FuzzingData fuzzData = Mockito.mock(FuzzingData.class);
        Mockito.when(fuzzData.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
        Mockito.when(fuzzData.getPayload()).thenReturn("{\"username\": \"test\"}");
        Mockito.when(fuzzData.getRequestPropertyTypes()).thenReturn(schemaMap);
        Mockito.when(fuzzData.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(fuzzData.getHeaders()).thenReturn(Set.of());
        Mockito.when(fuzzData.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(caller.call(Mockito.any())).thenReturn(
                CatsResponse.builder()
                        .responseCode(code)
                        .body(body)
                        .build());

        fuzzer.fuzz(fuzzData);

        if (shouldError) {
            Mockito.verify(listener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.contains(expectedMsg),
                    Mockito.anyString(), Mockito.any());
        } else {
            Mockito.verify(listener, Mockito.atLeastOnce()).reportResultInfo(
                    Mockito.any(), Mockito.any(), Mockito.contains(expectedMsg), Mockito.any());
        }
    }

    @Test
    void shouldHandleArrayPayload() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("username", new StringSchema());

        FuzzingData fuzzData = Mockito.mock(FuzzingData.class);
        Mockito.when(fuzzData.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
        Mockito.when(fuzzData.getPayload()).thenReturn("[{\"username\": \"test1\"}, {\"username\": \"test2\"}]");
        Mockito.when(fuzzData.getRequestPropertyTypes()).thenReturn(schemaMap);
        Mockito.when(fuzzData.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(fuzzData.getHeaders()).thenReturn(Set.of());
        Mockito.when(fuzzData.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(caller.call(Mockito.any())).thenReturn(
                CatsResponse.builder()
                        .responseCode(400)
                        .body("Bad Request")
                        .build());

        fuzzer.fuzz(fuzzData);

        Mockito.verify(caller, Mockito.atLeast(1)).call(Mockito.any());
        Mockito.verify(listener, Mockito.atLeastOnce()).reportResultInfo(
                Mockito.any(), Mockito.any(), Mockito.contains("Mass Assignment payload rejected"),
                Mockito.any());
    }
}
