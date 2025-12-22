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
class SSRFInUrlFieldsFuzzerTest {

    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private SimpleExecutor simpleExecutor;
    private SSRFInUrlFieldsFuzzer ssrfFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        ssrfFuzzer = new SSRFInUrlFieldsFuzzer(simpleExecutor, testCaseListener);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(ssrfFuzzer.description()).containsIgnoringCase("SSRF");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(ssrfFuzzer).hasToString("SSRFInUrlFieldsFuzzer");
    }

    @Test
    void shouldSkipForHeadAndTrace() {
        Assertions.assertThat(ssrfFuzzer.skipForHttpMethods())
                .containsOnly(HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "null"}, nullValues = "null")
    void shouldNotRunWithEmptyPayload(String payload) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn(payload);
        ssrfFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldNotRunWhenNoUrlFields() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("name", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name"));
        Mockito.when(data.getPayload()).thenReturn("{\"name\": \"test\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);

        ssrfFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldRunWhenUrlFieldPresent() {
        Map<String, Schema> reqTypes = new HashMap<>();
        StringSchema urlSchema = new StringSchema();
        urlSchema.setFormat("uri");
        reqTypes.put("callback", urlSchema);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("callback"));
        Mockito.when(data.getPayload()).thenReturn("{\"callback\": \"https://example.com\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

        ssrfFuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.atLeast(1)).call(Mockito.any());
    }

    @Test
    void shouldDetectUrlFieldByName() {
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("webhookUrl", new StringSchema());

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("webhookUrl"));
        Mockito.when(data.getPayload()).thenReturn("{\"webhookUrl\": \"https://example.com\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

        ssrfFuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.atLeast(1)).call(Mockito.any());
    }

    static Stream<Arguments> ssrfScenarios() {
        return Stream.of(
                Arguments.of("AWS metadata in response", 200,
                        "ami-id: ami-12345, instance-id: i-abcdef",
                        "Potential SSRF vulnerability", true),
                Arguments.of("GCP metadata in response", 200,
                        "computeMetadata: v1, project-id: my-project",
                        "Potential SSRF vulnerability", true),
                Arguments.of("Azure metadata in response", 200,
                        "subscriptionId: abc-123, resourceGroupName: my-rg",
                        "Potential SSRF vulnerability", true),
                Arguments.of("File content in response", 200,
                        "root:x:0:0:root:/root:/bin/bash",
                        "Potential SSRF vulnerability", true),
                Arguments.of("Localhost in response", 200,
                        "Connected to 127.0.0.1",
                        "Potential SSRF vulnerability", true),
                Arguments.of("Connection error in response", 200,
                        "connection refused to internal-service.local",
                        "Potential SSRF vulnerability", true),
                Arguments.of("DNS error in response", 200,
                        "getaddrinfo failed for internal.company.com",
                        "Potential SSRF vulnerability", true),
                Arguments.of("4xx rejection", 400,
                        "Bad Request",
                        "SSRF payload rejected", false),
                Arguments.of("5xx error", 500,
                        "Service Unavailable",
                        "Server error with SSRF payload", true),
                Arguments.of("2xx without indicators", 200,
                        "{\"status\": \"ok\"}",
                        "SSRF payload accepted", false),
                Arguments.of("3xx redirect", 301,
                        "Moved Permanently",
                        "Unexpected response code", true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ssrfScenarios")
    void shouldHandleSsrfScenarios(String scenario, int responseCode, String responseBody,
                                     String expectedMessageFragment, boolean shouldBeError) {
        Map<String, Schema> reqTypes = new HashMap<>();
        StringSchema urlSchema = new StringSchema();
        urlSchema.setFormat("uri");
        reqTypes.put("callback", urlSchema);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("callback"));
        Mockito.when(data.getPayload()).thenReturn("{\"callback\": \"https://example.com\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder()
                        .responseCode(responseCode)
                        .body(responseBody)
                        .build());

        ssrfFuzzer.fuzz(data);

        if (shouldBeError) {
            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment),
                    Mockito.anyString(), Mockito.any());
        } else {
            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                    Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment), Mockito.any());
        }
    }
}
