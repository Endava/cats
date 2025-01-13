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
                // Cloud metadata patterns
                Arguments.of("AWS metadata in response", 200,
                        "ami-id: ami-12345, instance-id: i-abcdef",
                        "Cloud metadata service accessed", true),
                Arguments.of("AWS IAM credentials path", 200,
                        "iam/security-credentials/role-name",
                        "Cloud metadata service accessed", true),
                Arguments.of("GCP metadata in response", 200,
                        "computeMetadata: v1, project-id: my-project",
                        "Cloud metadata service accessed", true),
                Arguments.of("GCP service accounts", 200,
                        "service-accounts/default/token",
                        "Cloud metadata service accessed", true),
                Arguments.of("Azure metadata in response", 200,
                        "subscriptionId: abc-123, resourceGroupName: my-rg",
                        "Cloud metadata service accessed", true),
                
                // File content patterns
                Arguments.of("File content Linux passwd", 200,
                        "root:x:0:0:root:/root:/bin/bash",
                        "File content exposed via SSRF", true),
                Arguments.of("File content BSD passwd", 200,
                        "root:*:0:0:Charlie Root:/root:/bin/sh",
                        "File content exposed via SSRF", true),
                Arguments.of("File path /etc/passwd in response", 200,
                        "Error reading /etc/passwd file",
                        "File content exposed via SSRF", true),
                Arguments.of("File path /etc/shadow in response", 200,
                        "Cannot access /etc/shadow",
                        "File content exposed via SSRF", true),
                Arguments.of("Shell path /bin/bash", 200,
                        "Using shell: /bin/bash",
                        "File content exposed via SSRF", true),
                Arguments.of("Shell path /bin/sh", 200,
                        "Default shell: /bin/sh",
                        "File content exposed via SSRF", true),
                
                // Network errors
                Arguments.of("Connection refused error", 200,
                        "connection refused to internal-service.local",
                        "Network error reveals SSRF attempt", true),
                Arguments.of("Connection timed out error", 200,
                        "connection timed out after 30s",
                        "Network error reveals SSRF attempt", true),
                Arguments.of("No route to host error", 200,
                        "no route to host 10.0.0.1",
                        "Network error reveals SSRF attempt", true),
                Arguments.of("Network unreachable error", 200,
                        "network unreachable",
                        "Network error reveals SSRF attempt", true),
                
                // DNS errors
                Arguments.of("DNS getaddrinfo error", 200,
                        "getaddrinfo failed for internal.company.com",
                        "DNS resolution error reveals SSRF attempt", true),
                Arguments.of("Could not resolve host", 200,
                        "could not resolve host: metadata.internal",
                        "DNS resolution error reveals SSRF attempt", true),
                Arguments.of("Name or service not known", 200,
                        "name or service not known",
                        "DNS resolution error reveals SSRF attempt", true),
                
                // HTTP client errors
                Arguments.of("Curl error in response", 200,
                        "curl error: couldn't connect to host",
                        "HTTP client error reveals SSRF attempt", true),
                Arguments.of("URLopen error in response", 200,
                        "urlopen error [Errno 111]",
                        "HTTP client error reveals SSRF attempt", true),
                Arguments.of("Socket error in response", 200,
                        "socket error [Errno 104]",
                        "HTTP client error reveals SSRF attempt", true),
                Arguments.of("Failed to connect error", 200,
                        "failed to connect [error 10061]",
                        "HTTP client error reveals SSRF attempt", true),
                
                // Internal target reflection (requires target in payload)
                Arguments.of("Localhost in response", 200,
                        "Connected to 127.0.0.1",
                        "Internal target reflected in response", true),
                Arguments.of("0.0.0.0 in response", 200,
                        "Binding to 0.0.0.0:8080",
                        "Internal target reflected in response", true),
                Arguments.of("AWS metadata IP in response", 200,
                        "Fetching from 169.254.169.254",
                        "Internal target reflected in response", true),
                Arguments.of("GCP metadata domain in response", 200,
                        "Querying metadata.google.internal",
                        "Internal target reflected in response", true),
                Arguments.of("Azure metadata domain in response", 200,
                        "Calling metadata.azure.com",
                        "SSRF payload accepted", false),
                
                // Response codes
                Arguments.of("4xx rejection", 400,
                        "Bad Request",
                        "SSRF payload rejected", false),
                Arguments.of("5xx error", 500,
                        "Service Unavailable",
                        "Server error", true),
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

    @Test
    void shouldDetectPayloadReflection() {
        Map<String, Schema> reqTypes = new HashMap<>();
        StringSchema urlSchema = new StringSchema();
        urlSchema.setFormat("uri");
        reqTypes.put("callback", urlSchema);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("callback"));
        Mockito.when(data.getPayload()).thenReturn("{\"callback\": \"http://localhost\"}");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getHeaders()).thenReturn(Set.of());
        Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

        // Response contains the exact SSRF payload
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder()
                        .responseCode(200)
                        .body("Connecting to http://localhost for data")
                        .build());

        ssrfFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                Mockito.any(), Mockito.any(), Mockito.eq("SSRF payload reflected in response"),
                Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldNotDetectInternalTargetWhenNotInPayload() {
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

        // Response contains a pattern that's NOT in any SSRF payload - should NOT trigger internal target detection
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder()
                        .responseCode(200)
                        .body("Service running on 192.168.1.1:8080")
                        .build());

        ssrfFuzzer.fuzz(data);

        // Should report INFO (accepted) not ERROR (internal target reflected)
        // Since 192.168.1.1 is not in the SSRF_PAYLOADS list, it won't trigger internal target detection
        Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                Mockito.any(), Mockito.any(), Mockito.contains("SSRF payload accepted"), Mockito.any());
        Mockito.verify(testCaseListener, Mockito.never()).reportResultError(
                Mockito.any(), Mockito.any(), Mockito.contains("Internal target reflected"),
                Mockito.anyString(), Mockito.any());
    }
}
