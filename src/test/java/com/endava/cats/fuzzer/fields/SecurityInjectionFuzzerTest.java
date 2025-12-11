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
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultWarn(
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
}
