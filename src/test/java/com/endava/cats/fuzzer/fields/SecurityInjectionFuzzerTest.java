package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
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
import org.junit.jupiter.api.Nested;
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
class SecurityInjectionFuzzerTest {

    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private SimpleExecutor simpleExecutor;
    private SecurityFuzzerArguments securityFuzzerArguments;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        securityFuzzerArguments = new SecurityFuzzerArguments();
    }

    @Nested
    @QuarkusTest
    class SqlInjectionFuzzerTest {
        private SqlInjectionInStringFieldsFuzzer sqlInjectionFuzzer;

        @BeforeEach
        void setup() {
            sqlInjectionFuzzer = new SqlInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(sqlInjectionFuzzer.description()).containsIgnoringCase("SQL injection");
        }

        @Test
        void shouldHaveToString() {
            Assertions.assertThat(sqlInjectionFuzzer).hasToString("SqlInjectionInStringFieldsFuzzer");
        }

        @Test
        void shouldSkipForHeadAndTrace() {
            Assertions.assertThat(sqlInjectionFuzzer.skipForHttpMethods())
                    .containsOnly(HttpMethod.HEAD, HttpMethod.TRACE);
        }

        @ParameterizedTest
        @CsvSource(value = {"''", "null"}, nullValues = "null")
        void shouldNotRunWithEmptyPayload(String payload) {
            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getPayload()).thenReturn(payload);
            sqlInjectionFuzzer.fuzz(data);
            Mockito.verifyNoInteractions(serviceCaller);
        }

        @Test
        void shouldNotRunWhenNoStringFields() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("count", new IntegerSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("count"));
            Mockito.when(data.getPayload()).thenReturn("{\"count\": 10}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);

            sqlInjectionFuzzer.fuzz(data);
            Mockito.verifyNoInteractions(serviceCaller);
        }

        @Test
        void shouldRunWhenStringFieldPresent() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

            sqlInjectionFuzzer.fuzz(data);

            // 10 SQL injection payloads (top payloads) for 1 string field
            Mockito.verify(serviceCaller, Mockito.times(10)).call(Mockito.any());
        }

        @Test
        void shouldRunAllPayloadsWhenFlagEnabled() {
            securityFuzzerArguments.setIncludeAllInjectionPayloads(true);
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

            sqlInjectionFuzzer.fuzz(data);

            // All 30 SQL injection payloads for 1 string field
            Mockito.verify(serviceCaller, Mockito.times(30)).call(Mockito.any());
        }

        @Test
        void shouldReportErrorWhenSqlErrorPatternDetected() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            // Return response with SQL error message
            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(500)
                            .body("Error: You have an error in your SQL syntax near...")
                            .build());

            sqlInjectionFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.contains("SQL"), Mockito.anyString(), Mockito.any());
        }

        @Test
        void shouldReportWarnWhenServerError() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            // Return 500 without SQL error pattern
            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(500)
                            .body("Internal Server Error")
                            .build());

            sqlInjectionFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        }

        @Test
        void shouldReportInfoWhenPayloadRejected() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            // Return 400 - properly rejected
            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(400)
                            .body("Bad Request")
                            .build());

            sqlInjectionFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                    Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any());
        }

        @Test
        void shouldReportInfoWhenPayloadAcceptedWith2xx() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(200)
                            .body("{\"status\": \"ok\"}")
                            .build());

            sqlInjectionFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                    Mockito.any(), Mockito.any(), Mockito.contains("accepted"), Mockito.any());
        }

        @Test
        void shouldReportInfoForUnexpectedResponseCode() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(302)
                            .body("")
                            .build());

            sqlInjectionFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.contains("Unexpected"), Mockito.any(), Mockito.any());
        }
    }

    @Nested
    @QuarkusTest
    class XssInjectionFuzzerTest {
        private XssInjectionInStringFieldsFuzzer xssFuzzer;

        @BeforeEach
        void setup() {
            xssFuzzer = new XssInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldReturnTrueForCheckForPayloadReflection() {
            Assertions.assertThat(xssFuzzer.shouldCheckForPayloadReflection()).isTrue();
        }

        @Test
        void shouldReturnAllInjectionTypes() {
            Assertions.assertThat(xssFuzzer.getAllInjectionPayloads()).containsAll(XssInjectionInStringFieldsFuzzer.ALL_XSS_PAYLOADS);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(xssFuzzer.description()).containsIgnoringCase("XSS");
        }

        @Test
        void shouldReportErrorWhenPayloadReflected() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("comment", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("comment"));
            Mockito.when(data.getPayload()).thenReturn("{\"comment\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            // Return response with reflected XSS payload
            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(200)
                            .body("{\"comment\": \"<script>alert('XSS')</script>\"}")
                            .build());

            xssFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.contains("XSS"), Mockito.any());
        }
    }

    @Nested
    @QuarkusTest
    class CommandInjectionFuzzerTest {
        private CommandInjectionInStringFieldsFuzzer commandFuzzer;

        @BeforeEach
        void setup() {
            commandFuzzer = new CommandInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldReturnAllInjectionTypes() {
            Assertions.assertThat(commandFuzzer.getAllInjectionPayloads()).containsAll(CommandInjectionInStringFieldsFuzzer.ALL_COMMAND_INJECTION_PAYLOADS);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(commandFuzzer.description()).containsIgnoringCase("command injection");
        }

        @Test
        void shouldReportErrorWhenCommandOutputDetected() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("filename", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("filename"));
            Mockito.when(data.getPayload()).thenReturn("{\"filename\": \"test.txt\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            // Return response with command output
            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(200)
                            .body("root:x:0:0:root:/root:/bin/bash")
                            .build());

            commandFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        }
    }

    @Nested
    @QuarkusTest
    class NoSqlInjectionFuzzerTest {
        private NoSqlInjectionInStringFieldsFuzzer noSqlFuzzer;

        @BeforeEach
        void setup() {
            noSqlFuzzer = new NoSqlInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(noSqlFuzzer.description()).containsIgnoringCase("NoSQL injection");
        }

        @Test
        void shouldReturnAllInjectionTypes() {
            Assertions.assertThat(noSqlFuzzer.getAllInjectionPayloads()).containsAll(NoSqlInjectionInStringFieldsFuzzer.ALL_NOSQL_INJECTION_PAYLOADS);
        }

        @Test
        void shouldReportErrorWhenMongoErrorDetected() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("query", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("query"));
            Mockito.when(data.getPayload()).thenReturn("{\"query\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            // Return response with MongoDB error
            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(500)
                            .body("MongoError: unknown operator: $gt")
                            .build());

            noSqlFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        }
    }

    @Nested
    @QuarkusTest
    class SSRFInUrlFieldsFuzzerTest {
        private SSRFInUrlFieldsFuzzer ssrfFuzzer;

        @BeforeEach
        void setup() {
            ssrfFuzzer = new SSRFInUrlFieldsFuzzer(simpleExecutor, testCaseListener);
        }

        @Test
        void shouldSkipForEmptyPayload() {
            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getPayload()).thenReturn("");
            ssrfFuzzer.fuzz(data);
            Mockito.verifyNoInteractions(serviceCaller);
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

        @Test
        void shouldSkipWhenNoUrlFields() {
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
        void shouldRunWhenUrlFieldByNamePresent() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("callbackUrl", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("callbackUrl"));
            Mockito.when(data.getPayload()).thenReturn("{\"callbackUrl\": \"https://example.com\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder().responseCode(400).build());

            ssrfFuzzer.fuzz(data);

            // All SSRF payloads for 1 URL field (URL fields are rare, so all payloads by default)
            Mockito.verify(serviceCaller, Mockito.times(43)).call(Mockito.any());
        }

        @Test
        void shouldRunWhenUriFormatFieldPresent() {
            StringSchema uriSchema = new StringSchema();
            uriSchema.setFormat("uri");
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("endpoint", uriSchema);

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("endpoint"));
            Mockito.when(data.getPayload()).thenReturn("{\"endpoint\": \"https://api.example.com\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder().responseCode(500).build());

            ssrfFuzzer.fuzz(data);

            Mockito.verify(serviceCaller, Mockito.times(43)).call(Mockito.any());
        }

        @Test
        void shouldReportErrorWhenMetadataPatternDetected() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("webhookUrl", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("webhookUrl"));
            Mockito.when(data.getPayload()).thenReturn("{\"webhookUrl\": \"https://example.com\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(200)
                            .body("{\"data\": \"ami-id: ami-12345, instance-id: i-abcdef\"}")
                            .build());

            ssrfFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.contains("SSRF"), Mockito.anyString(), Mockito.any());
        }

        @Test
        void shouldReportWarnFor5xxResponse() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("callbackUrl", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("callbackUrl"));
            Mockito.when(data.getPayload()).thenReturn("{\"callbackUrl\": \"https://example.com\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder().responseCode(503).body("").build());

            ssrfFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.contains("Server error"), Mockito.anyString(), Mockito.any());
        }

        @Test
        void shouldReportInfoFor2xxResponseWithoutSsrfIndicators() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("redirectUrl", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("redirectUrl"));
            Mockito.when(data.getPayload()).thenReturn("{\"redirectUrl\": \"https://example.com\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder().responseCode(200).body("{\"status\": \"ok\"}").build());

            ssrfFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                    Mockito.any(), Mockito.any(), Mockito.contains("accepted"), Mockito.any());
        }

        @Test
        void shouldReportWarnForUnexpectedResponseCode() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("targetUrl", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("targetUrl"));
            Mockito.when(data.getPayload()).thenReturn("{\"targetUrl\": \"https://example.com\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder().responseCode(302).body("").build());

            ssrfFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.contains("Unexpected"), Mockito.anyString(), Mockito.any());
        }

        @Test
        void shouldReportInfoFor4xxResponse() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("imageUrl", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("imageUrl"));
            Mockito.when(data.getPayload()).thenReturn("{\"imageUrl\": \"https://example.com/img.png\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder().responseCode(400).body("").build());

            ssrfFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                    Mockito.any(), Mockito.any(), Mockito.contains("rejected"), Mockito.any());
        }
    }

    @Nested
    @QuarkusTest
    class MassAssignmentFuzzerTest {
        private MassAssignmentFuzzer massAssignmentFuzzer;

        public static Stream<Arguments> getArgumentsForShouldReportError() {
            return Stream.of(
                    Arguments.of(200, "{\"name\": \"test\", \"isAdmin\": true}", "Mass Assignment"),
                    Arguments.of(302, "", "Unexpected"),
                    Arguments.of(500, "", "Server error"),
                    Arguments.of(200, "{\"status\": \"ok\"}", "Undeclared field accepted"));
        }

        @BeforeEach
        void setup() {
            massAssignmentFuzzer = new MassAssignmentFuzzer(simpleExecutor, testCaseListener);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(massAssignmentFuzzer.description()).containsIgnoringCase("mass assignment");
        }

        @Test
        void shouldHaveToString() {
            Assertions.assertThat(massAssignmentFuzzer).hasToString("MassAssignmentFuzzer");
        }

        @Test
        void shouldSkipGetDeleteHeadTrace() {
            Assertions.assertThat(massAssignmentFuzzer.skipForHttpMethods())
                    .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        }

        @Test
        void shouldSkipEmptyPayload() {
            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getPayload()).thenReturn("{}");

            massAssignmentFuzzer.fuzz(data);

            Mockito.verify(serviceCaller, Mockito.never()).call(Mockito.any());
        }

        @Test
        void shouldAddUndeclaredFieldsToPayload() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("name", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name"));
            Mockito.when(data.getPayload()).thenReturn("{\"name\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder().responseCode(400).build());

            massAssignmentFuzzer.fuzz(data);

            Mockito.verify(serviceCaller, Mockito.atLeast(1)).call(Mockito.any());
        }

        @ParameterizedTest
        @MethodSource("getArgumentsForShouldReportError")
        void shouldReportErrorWhen(int responseCode, String body, String expectedMessage) {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("name", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name"));
            Mockito.when(data.getPayload()).thenReturn("{\"name\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(responseCode)
                            .body(body)
                            .build());

            massAssignmentFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.any(), Mockito.contains(expectedMessage), Mockito.anyString(), Mockito.any());
        }

        @Test
        void shouldReportInfoWhenFieldRejected() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("name", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name"));
            Mockito.when(data.getPayload()).thenReturn("{\"name\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder().responseCode(400).build());

            massAssignmentFuzzer.fuzz(data);

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                    Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any());
        }
    }
}
